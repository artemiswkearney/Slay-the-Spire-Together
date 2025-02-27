package chronoMods.network;

import com.evacipated.cardcrawl.modthespire.lib.*;

import basemod.*;

import org.apache.logging.log4j.*;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.actions.*;
import com.megacrit.cardcrawl.actions.common.*;
import com.megacrit.cardcrawl.cards.*;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.rooms.*;
import com.megacrit.cardcrawl.map.*;
import com.megacrit.cardcrawl.rewards.*;
import com.megacrit.cardcrawl.relics.*;
import com.megacrit.cardcrawl.potions.*;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.ui.buttons.*;
import com.megacrit.cardcrawl.vfx.*;
import com.megacrit.cardcrawl.vfx.combat.*;
import com.megacrit.cardcrawl.screens.*;
import com.megacrit.cardcrawl.ui.*;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;
import com.badlogic.gdx.math.*;

import chronoMods.*;
import chronoMods.coop.*;
import chronoMods.coop.relics.*;
import chronoMods.coop.drawable.*;
import chronoMods.network.discord.DiscordIntegration;
import chronoMods.network.steam.*;
import chronoMods.ui.deathScreen.*;
import chronoMods.ui.hud.*;
import chronoMods.ui.lobby.*;
import chronoMods.ui.mainMenu.*;

import java.util.*;
import java.lang.*;
import java.nio.*;

import com.codedisaster.steamworks.*;

public class NetworkHelper {

	public static chronoMods.network.steam.SteamIntegration steam;
	//public static DiscordIntegration discord;
    public static ArrayList<Integration> networks = new ArrayList();

    public static ArrayList<Lobby> lobbies = new ArrayList();

    private static final Logger logger = LogManager.getLogger("Network Data");
    public static boolean embarked = false;

	public void NetworkHelper() {}

	public static void initialize() {
		// If Steam available, add SteamIntegration
		steam = new SteamIntegration();
		steam.initialize();

		if (steam.isInitialized()) {
			TogetherManager.log("Steam Started.");
			networks.add(steam);
		} else {
			TogetherManager.log("Steam Integration not found.");
		}
		// If Discord available, add DiscordIntegration
		DiscordIntegration discord = new DiscordIntegration();
		discord.initialize();
		if (discord.isInitialized()) {
			TogetherManager.log("Discord Started.");
			networks.add(discord);
		}
		else {
			TogetherManager.log("Discord Integration not found.");
		}
	}

    @SpirePatch(clz=CardCrawlGame.class, method="update")
    public static class SteamUpdate
    {
        public static void Postfix(CardCrawlGame __instance)
        {
        	NetworkHelper.update();
        }
    }

	// Check every frame for incoming packets.
	public static void update() {
		if (service() == null) { return; }

		Packet packet = service().getPacket();

		while (packet.hasPacket()) {

			if (TogetherManager.currentLobby != null)
				parseData(packet.data(), packet.player());

			packet = service().getPacket();
		} 
	}

	public static void parseData(ByteBuffer data, RemotePlayer playerInfo) {

		int enumIndex = data.getInt();
		if (enumIndex > dataType.values().length || enumIndex < 0) {
			TogetherManager.log("Unknown Enum value for data type: " + enumIndex);
			return;
		}
		dataType type = dataType.values()[enumIndex];

		switch (type) {

			case Version:

				playerInfo.version = data.getFloat(4);
				playerInfo.modHash = data.getInt(8);
				playerInfo.safeMods = data.getInt(12) == 1 ? true : false;

				TogetherManager.log("V: " + playerInfo.version);
				TogetherManager.log("H: " + playerInfo.modHash);
				TogetherManager.log("S: " + playerInfo.safeMods);
				break;
			case Rules:
				// Backup plan for slow loaders?
				if (NewMenuButtons.newGameScreen == null || NewMenuButtons.newGameScreen.ascensionSelectWidget == null) { return; }

				if (TogetherManager.gameMode != TogetherManager.mode.Coop) {
					NewMenuButtons.newGameScreen.characterSelectWidget.selectOption(data.getInt(4));
				}

				// Ascension
				NewMenuButtons.newGameScreen.ascensionSelectWidget.ascensionLevel = data.getInt(8);
				if (NewMenuButtons.newGameScreen.ascensionSelectWidget.ascensionLevel == 0) {
					NewMenuButtons.newGameScreen.ascensionSelectWidget.isAscensionMode = false;
				} else {
					NewMenuButtons.newGameScreen.ascensionSelectWidget.isAscensionMode = true;
				}

				// toggle boxes
				boolean heart = data.getInt(12)>0 ? true : false;
				NewMenuButtons.newGameScreen.heartToggle.setTicked(heart);
	            Settings.isFinalActAvailable = heart;

	            boolean neow = data.getInt(16)>0 ? true : false;
				NewMenuButtons.newGameScreen.neowToggle.setTicked(neow);
	            Settings.isTrial = !neow;

				boolean lament = data.getInt(20)>0 ? true : false;
				NewMenuButtons.newGameScreen.lamentToggle.setTicked(lament);
				if (lament) {
					NewMenuButtons.newGameScreen.neowToggle.setTicked(true);
		            Settings.isTrial = false;
				}
	            Settings.isTestingNeow = lament;

				boolean ironman = data.getInt(24)>0 ? true : false;
				NewMenuButtons.newGameScreen.ironmanToggle.setTicked(ironman);
	            NewDeathScreenPatches.Ironman = ironman;

				// seed
				Settings.seed = data.getLong(28);

				((Buffer)data).position(36);
				for (int b = 0; b < NewMenuButtons.customScreen.getActiveModData().size(); b++) {
					if (data.hasRemaining()) {
						if (data.get() == (byte)1) {
							NewMenuButtons.customScreen.modList.get(b).selected = true;
							TogetherManager.log("Selected: " + NewMenuButtons.customScreen.modList.get(b).name);
						}
						else {
							NewMenuButtons.customScreen.modList.get(b).selected = false;
							TogetherManager.log("Unselect: " + NewMenuButtons.customScreen.modList.get(b).name);
						}
					}
				}
				NewMenuButtons.customScreen.updateValues();

				// Update version every time you recieve a rules, as a fallback
				NetworkHelper.sendData(NetworkHelper.dataType.Version);
				// TogetherManager.log("Updated rules with Char " + data.getInt(4) + ", Asc " + data.getInt(8) + ", and seed " + data.getLong(12));
				break;
			case Start:
				TogetherManager.log("Start Run");
				NewMenuButtons.newGameScreen.embark();

				// Report to server - this is a blank entry to protect against rage quitters
				customMetrics startmetrics = new customMetrics();
				Thread st = new Thread((Runnable)startmetrics);
				st.start();

				break;
			case Ready:
				int start = data.getInt(4);
				if (start == 0) {
					playerInfo.ready = false;
					TogetherManager.log("Unready: " + playerInfo.userName);
				} else {
					playerInfo.ready = true;
					TogetherManager.log("Ready: " + playerInfo.userName);
				}
				break;
			case Floor:
				int floorNum = data.getInt(4);
				playerInfo.floor = floorNum;
				playerInfo.highestFloor = Math.max(floorNum, playerInfo.highestFloor);

				playerInfo.x = data.getInt(8);
				playerInfo.y = data.getInt(12);
				playerInfo.act = data.getInt(16);

				TogetherManager.log("Act: " + playerInfo.act + " - Floor: " + floorNum + " - Position: " + playerInfo.x + ", " + playerInfo.y);
				playerInfo.markMapNode();

				TopPanelPlayerPanels.SortWidgets();

				break;
			case Act:
				playerInfo.act = data.getInt(4);
				break;
			case Hp:
				int Hp = data.getInt(4);
				int maxHp = data.getInt(8);

				if (AbstractDungeon.player.hasBlight("MirrorTouch")) {

					if (!playerInfo.isUser(TogetherManager.currentUser)) {
						if (Hp > AbstractDungeon.player.currentHealth)
							AbstractDungeon.topLevelEffects.add(new HealNumberEffect(playerInfo.widget.x + 64f, playerInfo.widget.y, Hp - AbstractDungeon.player.currentHealth));
						else
							AbstractDungeon.topLevelEffects.add(new DamageNumberEffect(AbstractDungeon.player, playerInfo.widget.x + 64f, playerInfo.widget.y, AbstractDungeon.player.currentHealth - Hp));
					}

					AbstractDungeon.player.currentHealth = Hp;
					AbstractDungeon.player.maxHealth = maxHp;

	            	AbstractDungeon.player.healthBarUpdatedEvent();

	            	for (RemotePlayer playerhp : TogetherManager.players)
	            		playerhp.hp = Hp;
				}

				playerInfo.hp = Hp;
				playerInfo.maxHp = maxHp;
				TogetherManager.log("Player HP: " + Hp);
				break;
			case Money:
				int Money = data.getInt(4);

	            if (TogetherManager.gameMode == TogetherManager.mode.Coop && AbstractDungeon.player.hasBlight("DimensionalWallet")) {
	            	AbstractDungeon.player.gold = Money;
	            	for (RemotePlayer playergld : TogetherManager.players) {
	            		playergld.gold = Money;
	            	}
	            }

				playerInfo.gold = Money;
				TogetherManager.log("Gold: " + Money);
				break;
			case Character:
				// Extract the string
				try {
					String characterNameOut = NewMenuButtons.newGameScreen.characterSelectWidget.options.get(data.getInt(4)).c.getLocalizedCharacterName();
					playerInfo.character = characterNameOut;
				} catch (Exception e) {}
				break;
			case SetDisplayRelics:
				// Extract the string
				byte[] bytes = new byte[data.remaining()];
				data.get(bytes);
				String stringOut = new String(bytes);

				// Clear
				playerInfo.displayRelics.clear();

				// Make the relic
	 			for (String relicID : stringOut.split(",")) {
					AbstractRelic relic = RelicLibrary.getRelic(relicID).makeCopy();
					relic.isAnimating = true;
					playerInfo.displayRelics.add(relic);
					TogetherManager.log("Display Relic: " + relicID);
				}

				break;
			case SendRelic:
				long steamIDsr = data.getLong(4);
				if (!TogetherManager.currentUser.isUser(steamIDsr)) { break; }

				// Extract the string
				((Buffer)data).position(12);
				byte[] byteSentRelics = new byte[data.remaining()];
				data.get(byteSentRelics);
				String sentRelicID = new String(byteSentRelics);

				// Make the relic
				AbstractDungeon.getCurrRoom().spawnRelicAndObtain(Settings.WIDTH/2.0f, Settings.HEIGHT/2.0f, RelicLibrary.getRelic(sentRelicID).makeCopy());

				break;
			case Finish:
				float finishtime = data.getFloat(4);
				playerInfo.finalTime = finishtime;
				playerInfo.splits.get("Final").finish(finishtime);

				TopPanelPlayerPanels.SortWidgets();

				// Report to server - this should replace the earlier entry
				customMetrics metrics = new customMetrics();
				Thread t = new Thread((Runnable)metrics);
				t.start();

				break;

			case SendCard:
				// Find the correct recipient
				long steamIDs = data.getLong(4);
				if (!TogetherManager.currentUser.isUser(steamIDs)) { break; }

				// Get upgrade
				int upgrades = data.getInt(12);
				int miscs = data.getInt(16);

				// Extract the string
				((Buffer)data).position(20);
				byte[] bytess = new byte[data.remaining()];
				data.get(bytess);
				String stringOuts = new String(bytess);

				TogetherManager.log("Send card direct: " + stringOuts);

				// Creat RewardItem
				AbstractDungeon.player.masterDeck.addToTop(CardLibrary.getCopy(stringOuts, upgrades, miscs));

				break;
			case SendCardGhost:
				if (playerInfo.isUser(TogetherManager.currentUser)) { return; }

				// Get upgrade
				int upgradeghost = data.getInt(4);
				int miscghost = data.getInt(8);
				int update = data.getInt(12);
				int remove = data.getInt(16);

				// Extract the string
				((Buffer)data).position(20);
				byte[] bytesghost = new byte[data.remaining()];
				data.get(bytesghost);
				String stringOutghost = new String(bytesghost);

				TogetherManager.log("Send card ghost: " + stringOutghost);

				AbstractCard removeMe = null;
				// Add it to GhostWriter
				if (update > 0) {
					for (AbstractCard c : GhostWriter.rareCards.group) {
						if (c.cardID.equals(stringOutghost) && !c.upgraded) {
							c.upgrade();
							return;
						}
					}
				} else if (remove > 0) {
					for (AbstractCard c : GhostWriter.rareCards.group) {
						if (c.cardID.equals(stringOutghost) && c.timesUpgraded == upgradeghost)
							removeMe = c;
					}
					GhostWriter.rareCards.removeCard(removeMe);
				} else
					GhostWriter.rareCards.addToBottom(CardLibrary.getCopy(stringOutghost, upgradeghost, miscghost));

				break;
			case TransferCard:
				// Find the correct recipient
				long steamIDc = data.getLong(4);
				if (!TogetherManager.currentUser.isUser(steamIDc)) { break; }

				// Get upgrade
				int upgradec = data.getInt(12);
				int miscc = data.getInt(16);

				// Hardcoded relic shit because that's how we roll now
				if (AbstractDungeon.player.hasBlight("PneumaticPost")){
					TogetherManager.log("Upgrading");
					upgradec++;
				}

				// Extract the string
				((Buffer)data).position(20);
				byte[] bytesc = new byte[data.remaining()];
				data.get(bytesc);
				String stringOutc = new String(bytesc);

				TogetherManager.log("Transfer card: " + stringOutc);

				// Creat RewardItem
	            RewardItem transferItemc = new RewardItem();
	            transferItemc.cards.clear();
	            transferItemc.cards.add(CardLibrary.getCopy(stringOutc, upgradec, miscc));

	            // Add Reward to Packages for pickup
	            TogetherManager.getCurrentUser().packages.add(transferItemc);
				break;
			case TransferRelic:
				// Find the correct recipient
				long steamIDr = data.getLong(4);
				if (!TogetherManager.currentUser.isUser(steamIDr)) { break; }

				// Extract the string
				((Buffer)data).position(12);
				byte[] bytesr = new byte[data.remaining()];
				data.get(bytesr);
				String stringOutr = new String(bytesr);

				TogetherManager.log("Transfer relic: " + stringOutr);

				// Creat RewardItem
	            RewardItem transferItemr = new RewardItem(RelicLibrary.getRelic(stringOutr).makeCopy());

	            // Add Reward to Packages for pickup
	            TogetherManager.getCurrentUser().packages.add(transferItemr);
				break;
			case TransferPotion:
				// Find the correct recipient
				long steamIDp = data.getLong(4);
				if (!TogetherManager.currentUser.isUser(steamIDp)) { break; }

				// Extract the string
				((Buffer)data).position(12);
				byte[] bytesp = new byte[data.remaining()];
				data.get(bytesp);
				String stringOutp = new String(bytesp);

				TogetherManager.log("Transfer potion: " + stringOutp);

				// Creat RewardItem
	            RewardItem transferItemp = new RewardItem(PotionHelper.getPotion(stringOutp));

	            // Add Reward to Packages for pickup
	            TogetherManager.getCurrentUser().packages.add(transferItemp);
				break;
			case UsePotion:
				// Find the correct recipient
				int potslot = data.getInt(4);
				AbstractDungeon.player.potions.set(potslot, new PotionSlot(potslot));
				AbstractDungeon.topPanel.potionUi.close();
				break;
			case SendPotion:
				// Find the correct recipient
				int potslotb = data.getInt(4);

				// Extract the string
				((Buffer)data).position(8);
				byte[] bytesb = new byte[data.remaining()];
				data.get(bytesb);
				String stringOutb = new String(bytesb);

				TogetherManager.log("Send potion: " + stringOutb);

				// Obtain the potion
				if (AbstractDungeon.player.potions.get(potslotb) instanceof PotionSlot) {
		            AbstractDungeon.player.obtainPotion(potslotb, PotionHelper.getPotion(stringOutb));
		        }

				break;
			// case BossChosen:
			// 	data.getChar(1, );
			// 	break;
			case Splits:
				int actNum = data.getInt(4);
				float playtime = data.getFloat(8);

				TogetherManager.log("Splits, Act: " + (actNum-1) + " - " + VersusTimer.returnTimeString(playtime));
				switch (actNum) {
					case 1:
						playerInfo.splits.get("Act 1").activate(AbstractDungeon.bossKey);
						break;
					case 2:
						playerInfo.splits.get("Act 1").finish(playtime);
						playerInfo.splits.get("Act 2").activate(AbstractDungeon.bossKey);
						break;
					case 3:
						playerInfo.splits.get("Act 2").finish(playtime);
						playerInfo.splits.get("Act 3").activate(AbstractDungeon.bossKey);
						break;
					case 4:
						playerInfo.splits.get("Act 3").finish(playtime);
						playerInfo.splits.get("Final").activate(AbstractDungeon.bossKey);
						break;
					default:
						playerInfo.splits.get("Final").finish(playtime);
						break;
				}

				TopPanelPlayerPanels.SortWidgets();
				break;

			case ClearRoom:
				int xc = data.getInt(4);
				int yc = data.getInt(8);
				if (xc != -1 && yc != -1 && yc < 16 && !AbstractDungeon.id.equals("TheEnding")) {
		            MapRoomNode currentNodec = AbstractDungeon.map.get(yc).get(xc);

		            // Safety first? This triggers if games are desynced, but I hate getting reports about it.
		            if (currentNodec == null) 			{ return; }
		            if (currentNodec.getRoom() == null) { return; }

		            // Unlocks a room we are leaving
					CoopEmptyRoom.LockedRoomField.locked.set(currentNodec.getRoom(), false);
				
					// Sets the next room of a multi-room
					AbstractRoom secondRoom = CoopMultiRoom.secondRoomField.secondRoom.get(currentNodec);
					AbstractRoom thirdRoom  = CoopMultiRoom.thirdRoomField.thirdRoom.get(currentNodec);

					// Resolve the multinodes by advancing the 'queue' 
					currentNodec.room = secondRoom;
					CoopMultiRoom.secondRoomField.secondRoom.set(currentNodec, thirdRoom);
					CoopMultiRoom.thirdRoomField.thirdRoom.set(currentNodec, null);

					if (currentNodec.room == null)
						currentNodec.setRoom(new CoopEmptyRoom());
				}

				TogetherManager.log("Clearing: " + xc + ", " + yc);
				break;

			case LockRoom:
				int xl = data.getInt(4);
				int yl = data.getInt(8);
				try {
					if (xl != -1 && yl != -1 && yl < 16 && !AbstractDungeon.id.equals("TheEnding")) {
			            MapRoomNode currentNodel = AbstractDungeon.map.get(yl).get(xl);

						CoopEmptyRoom.LockedRoomField.locked.set(currentNodel.getRoom(), true);
					}
				} catch (Exception e) {}
				TogetherManager.log("Locking: " + xl + ", " + yl);
				break;

			case ChooseNeow:
				int choice = data.getInt(4);

				if (playerInfo.userName == null)
					playerInfo.userName = "Unknown Player";

				if (CoopNeowEvent.screenNum == 1)
					CoopNeowEvent.rewards.get(choice).chosenBy = playerInfo.userName;
				else 
					CoopNeowEvent.penalties.get(choice).chosenBy = playerInfo.userName;

	        	String neowMsg = String.format(CardCrawlGame.languagePack.getUIString("Neow").TEXT[0], playerInfo.userName, AbstractDungeon.getCurrRoom().event.roomEventText.optionList.get(choice).msg);

				AbstractDungeon.getCurrRoom().event.roomEventText.optionList.get(choice).msg = neowMsg;
				AbstractDungeon.getCurrRoom().event.roomEventText.optionList.get(choice).isDisabled = true;

				if (playerInfo.isUser(TogetherManager.currentUser)) {
					for (LargeDialogOptionButton choiceButton : AbstractDungeon.getCurrRoom().event.roomEventText.optionList) {
						choiceButton.isDisabled = true;
					}
				}

				boolean singleAllowance = false;

				if (CoopNeowEvent.screenNum == 1) {

					// Stop here if not everyone has chosen
					for (CoopNeowReward r : CoopNeowEvent.rewards) {
						if (r.chosenBy == "") { 
							if (singleAllowance) {
								return; }
							else {
								singleAllowance = true;
							}
						}
					}
				} else {

					for (CoopNeowReward r : CoopNeowEvent.penalties) {
						if (r.chosenBy == "") { 
							if (singleAllowance) {
								return; }
							else {
								singleAllowance = true;
							}
						}
					}
				}

				TogetherManager.log("Advance the screen!");

				CoopNeowEvent.advanceScreen();

				break;

			case ChooseTeamRelic:
				int choicer = data.getInt(4);

				if (playerInfo.isUser(TogetherManager.currentUser)) { break; }

				// Set your current choice
				CoopBossRelicSelectScreen teamScreen = TogetherManager.teamRelicScreen;

				for (ArrayList<RemotePlayer> pList : teamScreen.selected) {
					pList.remove(playerInfo);
				}
				teamScreen.selected.get(choicer).add(playerInfo);

				// Advance if selected
				if (teamScreen.selected.get(choicer).size() == TogetherManager.players.size()) {
					teamScreen.blights.get(choicer).obtain();
					teamScreen.blights.get(choicer).isObtained = true;
				}
				break;

			case LoseLife:
				int counter = data.getInt(4);

				if (counter >= 0) {
					// Death notification
					AbstractDungeon.effectList.add(new CoopDeathNotification(playerInfo));

					if (AbstractDungeon.player.hasBlight("StringOfFate")) {
						// Lower the counter
						AbstractDungeon.player.getBlight("StringOfFate").counter = counter;

						AbstractDungeon.player.decreaseMaxHealth(AbstractDungeon.player.maxHealth / 4); // +2 because -1 for the life lost, and -1 for zero index
				        if (AbstractDungeon.player.currentHealth > AbstractDungeon.player.maxHealth)
				            AbstractDungeon.player.currentHealth = AbstractDungeon.player.maxHealth;

				    } else if (AbstractDungeon.player.hasBlight("BondsOfFate")) {
						// Lower the counter
						AbstractDungeon.player.getBlight("BondsOfFate").counter = counter;

						((Buffer)data).position(8);
						byte[] bytesll = new byte[data.remaining()];
						data.get(bytesll);
						String killedBy = new String(bytesll);

						TogetherManager.log("Killed by: " + killedBy);
						if (killedBy == null || killedBy == "") {
							AbstractDungeon.topLevelEffects.add(new ShowCardAndObtainEffect(
								new Tombstone(playerInfo.userName, "", playerInfo.portraitImg), Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));
							if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT) {
								AbstractDungeon.actionManager.addToBottom((AbstractGameAction)new MakeTempCardInHandAction(new Tombstone(playerInfo.userName, "", playerInfo.portraitImg), 1)); 
							}
						} else {
							AbstractDungeon.topLevelEffects.add(new ShowCardAndObtainEffect(
								new Tombstone(playerInfo.userName, MonsterHelper.getEncounterName(killedBy), playerInfo.portraitImg), Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));
							if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT) {
								AbstractDungeon.actionManager.addToBottom((AbstractGameAction)new MakeTempCardInHandAction(new Tombstone(playerInfo.userName, "", playerInfo.portraitImg), 1)); 
							}
						}
				    }

				} else {
					// Die
					AbstractDungeon.player.currentHealth = 0;
					AbstractDungeon.player.isDead = true;

		            NewDeathScreenPatches.raceEndScreen = new RaceEndScreen(AbstractDungeon.getCurrRoom().monsters);
		            AbstractDungeon.screen = NewDeathScreenPatches.Enum.RACEEND;
					}

					NetworkHelper.sendData(NetworkHelper.dataType.Hp);
				break;

			case Kick:
				long steamIDk = data.getLong(4);
				if (TogetherManager.currentUser.isUser(steamIDk)) {
					NetworkHelper.leaveLobby();
					TogetherManager.infoPopup.show(CardCrawlGame.languagePack.getUIString("Network").TEXT[0], CardCrawlGame.languagePack.getUIString("Network").TEXT[1]);
				} else {
					RemotePlayer kickID = null;
					for (RemotePlayer playerkick : TogetherManager.players) {
						if (playerkick.getAccountID() == steamIDk)
							kickID = playerkick;
					}
					if (kickID != null)
						removePlayer(kickID);			
				}

				break;

			case GetRedKey:
				long steamIDrk = data.getLong(4);

				for (RemotePlayer playerrk : TogetherManager.players) {
					if (playerrk.isUser(steamIDrk))
						playerrk.rubyKey = true;
				}

				if (TogetherManager.currentUser.isUser(steamIDrk) && !Settings.hasRubyKey) {
					AbstractDungeon.topLevelEffects.add(new ObtainKeyEffect(ObtainKeyEffect.KeyColor.RED)); 
					AbstractDungeon.topLevelEffects.add(new SpeechTextEffect(Settings.WIDTH/2.0f, Settings.HEIGHT/2.0f, 5f, "#r" + playerInfo.userName + CardCrawlGame.languagePack.getUIString("Keys").TEXT[0], DialogWord.AppearEffect.FADE_IN));
				}

				break;

			case GetBlueKey:
				long steamIDbk = data.getLong(4);

				for (RemotePlayer playerbk : TogetherManager.players) {
					if (playerbk.isUser(steamIDbk))
						playerbk.sapphireKey = true;
				}

				if (TogetherManager.currentUser.isUser(steamIDbk) && !Settings.hasSapphireKey) {
					AbstractDungeon.topLevelEffects.add(new ObtainKeyEffect(ObtainKeyEffect.KeyColor.BLUE)); 
					AbstractDungeon.topLevelEffects.add(new SpeechTextEffect(Settings.WIDTH/2.0f, Settings.HEIGHT/2.0f, 5f, "#b" + playerInfo.userName + CardCrawlGame.languagePack.getUIString("Keys").TEXT[1], DialogWord.AppearEffect.FADE_IN));
				}

				break;

			case GetGreenKey:
				long steamIDgk = data.getLong(4);

				for (RemotePlayer playergk : TogetherManager.players) {
					if (playergk.isUser(steamIDgk))
						playergk.emeraldKey = true;
				}

				if (TogetherManager.currentUser.isUser(steamIDgk) && !Settings.hasEmeraldKey) {
					AbstractDungeon.topLevelEffects.add(new ObtainKeyEffect(ObtainKeyEffect.KeyColor.GREEN)); 
					AbstractDungeon.topLevelEffects.add(new SpeechTextEffect(Settings.WIDTH/2.0f, Settings.HEIGHT/2.0f, 5f, "#g" + playerInfo.userName + CardCrawlGame.languagePack.getUIString("Keys").TEXT[2], DialogWord.AppearEffect.FADE_IN));
				}

				break;
			case GetPotion:
				playerInfo.potionSlots = data.getInt(4);

				// Extract the string
				((Buffer)data).position(8);
				byte[] potionBytes = new byte[data.remaining()];
				data.get(potionBytes);
				String potionsOut = new String(potionBytes);

				// Clear
				playerInfo.potions.clear();

				// Add the owned potions to the list
	 			for (String potionID : potionsOut.split(",")) {
	 				if (!potionID.equals(""))
						playerInfo.potions.add(potionID);
				}
				break;
			case AddPotionSlot:
			    AbstractDungeon.player.potionSlots += 1;
			    AbstractDungeon.player.potions.add(new PotionSlot(AbstractDungeon.player.potionSlots - 1));
					break;
			case ModifyBrainFreeze:
				AbstractDungeon.player.getBlight("BrainFreeze").counter += data.getInt(4);
				break;

			case DrawMap:
				if (playerInfo.isUser(TogetherManager.currentUser)) { break; }

				float xSize = playerInfo.drawable[playerInfo.act-1].pixmap.getWidth();
				float ySize = playerInfo.drawable[playerInfo.act-1].pixmap.getHeight();

				Vector2 curr = new Vector2(data.getFloat(4)  * xSize, data.getFloat(8)  * ySize);
				Vector2 last = new Vector2(data.getFloat(12) * xSize, data.getFloat(16) * ySize);

				playerInfo.drawable[playerInfo.act-1].brushSize = data.getFloat(20);
				float offset = data.getFloat(24) * ySize;

				if (last.x == 0f && last.y == 0f)
					playerInfo.drawable[playerInfo.act-1].draw(curr, offset);
				else
					playerInfo.drawable[playerInfo.act-1].drawLerped(curr, last, offset);

				playerInfo.drawable[playerInfo.act-1].dirty = true;

				break;
			case ClearMap:
				if (playerInfo.isUser(TogetherManager.currentUser)) { break; }

				TogetherManager.log(playerInfo.userName + " has cleared their map.");

				playerInfo.drawable[playerInfo.act-1].clear();
				break;
			case DeckInfo:
				playerInfo.cards = data.getInt(4);
				playerInfo.upgrades = data.getInt(8);

				// Get upgrade
				int upgradeDeckCard = data.getInt(12);
				int miscDeckCard = data.getInt(16);
				int updateDeckCard = data.getInt(20);
				int removeDeckCard = data.getInt(24);

				// Extract the string
				((Buffer)data).position(28);
				byte[] bytesDeckCard = new byte[data.remaining()];
				data.get(bytesDeckCard);
				String stringOutDeckCard = new String(bytesDeckCard);

				TogetherManager.log("Update Deck Cards: " + stringOutDeckCard);

				AbstractCard removeMeFromDeck = null;
				// Add it to the deck
				if (updateDeckCard > 0) {
					for (AbstractCard c : playerInfo.deck.group) {
						if (c.cardID.equals(stringOutDeckCard) && !c.upgraded) {
							c.upgrade();
							return;
						}
					}
				} else if (removeDeckCard > 0) {
					for (AbstractCard c : playerInfo.deck.group) {
						if (c.cardID.equals(stringOutDeckCard) && c.timesUpgraded == upgradeDeckCard)
							removeMeFromDeck = c;
					}
					playerInfo.deck.removeCard(removeMeFromDeck);
				} else
					playerInfo.deck.addToBottom(CardLibrary.getCopy(stringOutDeckCard, upgradeDeckCard, miscDeckCard));

				playerInfo.widget.updateCardDisplay();

				break;
			case RelicInfo:
				playerInfo.relics = data.getInt(4);
				break;
			case RequestVersion:
				sendData(dataType.Version);
				break;
			case SendCardMessageBottle:
				if (playerInfo.isUser(TogetherManager.currentUser)) { break; }

				// Get upgrade
				int upgrademb = data.getInt(4);
				int miscmb = data.getInt(8);

				// Extract the string
				((Buffer)data).position(12);
				byte[] bytesmb = new byte[data.remaining()];
				data.get(bytesmb);
				String stringOutmb = new String(bytesmb);

				TogetherManager.log("Send card message bottle: " + stringOutmb);

				// Add the card and update text
				MessageInABottle.bottleCards.addToBottom(CardLibrary.getCopy(stringOutmb, upgrademb, miscmb));
				((MessageInABottle)AbstractDungeon.player.getBlight("MessageInABottle")).setDescriptionAfterLoading();

				break;
		}
	}

    public static enum dataType
    {
      	Rules, Start, Ready, Version, Floor, Act, Hp, Money, BossRelic, Finish, SendCard, SendCardGhost, TransferCard, TransferRelic, TransferPotion, UsePotion, SendPotion, EmptyRoom, BossChosen, Splits, SetDisplayRelics, ClearRoom, LockRoom, ChooseNeow, ChooseTeamRelic, LoseLife, Kick, GetRedKey, GetBlueKey, GetGreenKey, Character, GetPotion, AddPotionSlot, SendRelic, ModifyBrainFreeze, DrawMap, ClearMap, DeckInfo, RelicInfo, RequestVersion, SendCardMessageBottle;
      
    	private dataType() {}
    }

	public static void sendData(NetworkHelper.dataType type) {
		ByteBuffer data = NetworkHelper.generateData(type);	
		if (data == null) { return; }

		service().sendPacket(data);
	}

	private static ByteBuffer generateData(NetworkHelper.dataType type) {
		ByteBuffer data;

		switch (type) {

			// Packets used by both
			case Version:
				data = ByteBuffer.allocateDirect(32);
				data.putFloat(4, TogetherManager.VERSION);
				data.putInt(8, TogetherManager.modHash);
				data.putInt(12, TogetherManager.safeMods ? 1 : 0);
				break;
			case Rules:
        		if (!TogetherManager.currentLobby.isOwner()) { return null; }

				data = ByteBuffer.allocateDirect(36 + NewMenuButtons.customScreen.getActiveModData().size());
				// Rules are character, ascension, seed
				data.putInt(4, NewMenuButtons.newGameScreen.characterSelectWidget.getChosenOption());

				if (NewMenuButtons.newGameScreen.ascensionSelectWidget.isAscensionMode)
					data.putInt(8, NewMenuButtons.newGameScreen.ascensionSelectWidget.ascensionLevel);
				else
					data.putInt(8, 0);

				data.putInt(12, NewMenuButtons.newGameScreen.heartToggle.getTicked());
				data.putInt(16, NewMenuButtons.newGameScreen.neowToggle.getTicked());
				data.putInt(20, NewMenuButtons.newGameScreen.lamentToggle.getTicked());
				data.putInt(24, NewMenuButtons.newGameScreen.ironmanToggle.getTicked());

				if (Settings.seed != null){
					data.putLong(28, Settings.seed);
				} else {
					data.putLong(28, 0);
				}

				((Buffer)data).position(36);
				for (boolean on : NewMenuButtons.customScreen.getActiveModData()) {
					if (on)
						data.put((byte)1);
					else
						data.put((byte)0);
				}
				((Buffer)data).rewind();

				updateLobbyData();
				break;
			case Start:
				data = ByteBuffer.allocateDirect(8);
				data.putInt(4, 1);
				break;
			case Ready:
				data = ByteBuffer.allocateDirect(8);
				TogetherManager.log("Sending ready state: " + TogetherManager.getCurrentUser().userName + ", " + TogetherManager.getCurrentUser().ready);

				if (TogetherManager.getCurrentUser().ready) {
					TogetherManager.log("Sent Ready");
					data.putInt(4, 1);
				} else {
					TogetherManager.log("Sent Unready");
					data.putInt(4, 0);
				}
				break;
			case Floor:
				data = ByteBuffer.allocateDirect(20);
				data.putInt(4, AbstractDungeon.floorNum);
				data.putInt(8, AbstractDungeon.getCurrMapNode().x);
				data.putInt(12, AbstractDungeon.getCurrMapNode().y);
				data.putInt(16, AbstractDungeon.actNum);
				break;
			case Act:
				data = ByteBuffer.allocateDirect(8);
				data.putInt(4, AbstractDungeon.actNum);
				break;
			case Hp:
				data = ByteBuffer.allocateDirect(12);
				data.putInt(4, AbstractDungeon.player.currentHealth);
				data.putInt(8, AbstractDungeon.player.maxHealth);
				break;
			case Money:
				data = ByteBuffer.allocateDirect(8);
				data.putInt(4, AbstractDungeon.player.gold);
				break;
			case Character:
				data = ByteBuffer.allocateDirect(8);
				data.putInt(4, NewMenuButtons.newGameScreen.characterSelectWidget.getChosenOption());
				// String characterName = NewMenuButtons.newGameScreen.characterSelectWidget.getChosenOptionLocalizedName();
				// data = ByteBuffer.allocateDirect(4 + characterName.getBytes().length);

				// ((Buffer)data).position(4);
				// data.put(characterName.getBytes());
				// ((Buffer)data).rewind();
				break;
			case SetDisplayRelics:
				String relicID = "";

				for (AbstractRelic relic : AbstractDungeon.player.relics) {
					if (relic.tier == AbstractRelic.RelicTier.STARTER || relic.tier == AbstractRelic.RelicTier.BOSS) {
						relicID += relic.relicId + ",";
					}
				}

				relicID = relicID.substring(0, relicID.length() - 1);
				data = ByteBuffer.allocateDirect(4 + relicID.getBytes().length);

				((Buffer)data).position(4);
				data.put(relicID.getBytes());
				((Buffer)data).rewind();
				break;
			case SendRelic:
				data = ByteBuffer.allocateDirect(12 + Dimensioneel.relicID.getBytes().length);
				data.putLong(4, Dimensioneel.sendPlayer.getAccountID()); // Selected recipient

				((Buffer)data).position(12);
				data.put(Dimensioneel.relicID.getBytes());
				((Buffer)data).rewind();
				break;
			case Finish:
				data = ByteBuffer.allocateDirect(8);
				data.putFloat(4, VersusTimer.timer);
				break;

			// Versus specific
			case Splits:
				data = ByteBuffer.allocateDirect(12);
				data.putInt(4, AbstractDungeon.actNum);
				data.putFloat(8, VersusTimer.timer);
				break;

			// Coop specific packets
			case ClearRoom:
				data = ByteBuffer.allocateDirect(12);
				data.putInt(4, AbstractDungeon.getCurrMapNode().x);
				data.putInt(8, AbstractDungeon.getCurrMapNode().y);
				break;
			case LockRoom:
				data = ByteBuffer.allocateDirect(12);
				data.putInt(4, SendDataPatches.lockX);
				data.putInt(8, SendDataPatches.lockY);
				break;

			case SendCard:
				String rewards = GhostWriter.sendCard.cardID;

				data = ByteBuffer.allocateDirect(20 + rewards.getBytes().length);

				data.putLong(4, GhostWriter.sendPlayer.getAccountID()); // Selected recipient
				data.putInt(12, GhostWriter.sendCard.timesUpgraded);
				data.putInt(16, GhostWriter.sendCard.misc);

				((Buffer)data).position(20);
				data.put(rewards.getBytes());
				((Buffer)data).rewind();

				GhostWriter.sendCard = null; 
				break;
			case SendCardGhost:
				String rewardghost = GhostWriter.sendCard.cardID;

				data = ByteBuffer.allocateDirect(20 + rewardghost.getBytes().length);

				data.putInt(4, GhostWriter.sendCard.timesUpgraded);
				data.putInt(8, GhostWriter.sendCard.misc);
				data.putInt(12, GhostWriter.sendUpdate ? 1 : 0);
				data.putInt(16, GhostWriter.sendRemove ? 1 : 0);

				((Buffer)data).position(20);
				data.put(rewardghost.getBytes());
				((Buffer)data).rewind();

				GhostWriter.sendCard = null; 
				break;
			case TransferCard:
				String rewardc = TogetherManager.courierScreen.transferCard.cardID;

				data = ByteBuffer.allocateDirect(20 + rewardc.getBytes().length);

				data.putLong(4, TogetherManager.courierScreen.getRecipient().getAccountID()); // Selected recipient
				data.putInt(12, TogetherManager.courierScreen.transferCard.timesUpgraded);
				data.putInt(16, TogetherManager.courierScreen.transferCard.misc);

				((Buffer)data).position(20);
				data.put(rewardc.getBytes());
				((Buffer)data).rewind();

				TogetherManager.courierScreen.transferCard = null; 
				break;
			case TransferRelic:
				String rewardr = TogetherManager.courierScreen.transferRelic.relicId;

				data = ByteBuffer.allocateDirect(12 + rewardr.getBytes().length);

				data.putLong(4, TogetherManager.courierScreen.getRecipient().getAccountID()); // Selected recipient

				((Buffer)data).position(12);
				data.put(rewardr.getBytes());
				((Buffer)data).rewind();

				TogetherManager.courierScreen.transferRelic = null; 
				break;
			case TransferPotion:
				String rewardp = TogetherManager.courierScreen.transferPotion.ID;

				data = ByteBuffer.allocateDirect(12 + rewardp.getBytes().length);

				data.putLong(4, TogetherManager.courierScreen.getRecipient().getAccountID()); // Selected recipient

				((Buffer)data).position(12);
				data.put(rewardp.getBytes());
				((Buffer)data).rewind();

				TogetherManager.courierScreen.transferPotion = null; 
				break;
			case UsePotion:
				data = ByteBuffer.allocateDirect(8);
				data.putInt(4, VaporFunnel.potSlot);
				break;
			case SendPotion:
				String rewardb = VaporFunnel.potName;
				TogetherManager.log(VaporFunnel.potName);
				data = ByteBuffer.allocateDirect(8 + rewardb.getBytes().length);

				data.putInt(4, VaporFunnel.potSlot); // Selected recipient

				((Buffer)data).position(8);
				data.put(rewardb.getBytes());
				((Buffer)data).rewind();
				break;
			// case BossChosen:
			// 	data.allocate(3);
			// 	data.putChar(1, );
			// 	break;

			case ChooseNeow:
				data = ByteBuffer.allocateDirect(8);
				data.putInt(4, CoopNeowEvent.chosenOption);
				break;
			case ChooseTeamRelic:
				data = ByteBuffer.allocateDirect(8);
				data.putInt(4, TogetherManager.teamRelicScreen.selectedIndex);
				break;

			case LoseLife:
				if (AbstractDungeon.player.hasBlight("BondsOfFate")){
					if (AbstractDungeon.lastCombatMetricKey != null) {
						String killedBy = AbstractDungeon.lastCombatMetricKey;
						data = ByteBuffer.allocateDirect(8 + killedBy.getBytes().length);
						data.putInt(4, AbstractDungeon.player.getBlight("BondsOfFate").counter);

						((Buffer)data).position(8);
						data.put(killedBy.getBytes());
						((Buffer)data).rewind();
					} else {
						data = ByteBuffer.allocateDirect(8);
						data.putInt(4, AbstractDungeon.player.getBlight("BondsOfFate").counter);
					}
				}
				else {
					data = ByteBuffer.allocateDirect(8);
					data.putInt(4, AbstractDungeon.player.getBlight("StringOfFate").counter);
				}
				break;

			case Kick:
				data = ByteBuffer.allocateDirect(16);
				data.putLong(4, NewGameScreen.kick.getAccountID());
				break;

			case GetRedKey:
				data = ByteBuffer.allocateDirect(16);
				data.putLong(4, CoopKeySharing.redKeyPlayer.getAccountID());
				break;

			case GetBlueKey:
				data = ByteBuffer.allocateDirect(16);
				data.putLong(4, CoopKeySharing.blueKeyPlayer.getAccountID());
				break;

			case GetGreenKey:
				data = ByteBuffer.allocateDirect(16);
				data.putLong(4, CoopKeySharing.greenKeyPlayer.getAccountID());
				break;

			case GetPotion:
				String potionsHeld = "";

				for (AbstractPotion potion : AbstractDungeon.player.potions) {
					potionsHeld += potion.ID;
					potionsHeld += ",";
				}
				potionsHeld = potionsHeld.substring(0, potionsHeld.length() - 1);

				data = ByteBuffer.allocateDirect(8 + potionsHeld.getBytes().length);

				data.putInt(4, AbstractDungeon.player.potionSlots);

				((Buffer)data).position(8);
				data.put(potionsHeld.getBytes());
				((Buffer)data).rewind();
				break;
			case AddPotionSlot:
				data = ByteBuffer.allocateDirect(4);
				break;
			case ModifyBrainFreeze:
				data = ByteBuffer.allocateDirect(8);
				data.putInt(4, BrainFreeze.modEnergy);
				BrainFreeze.modEnergy = 0;
				break;
			case DrawMap:
				data = ByteBuffer.allocateDirect(28);
				MapCanvas c = TogetherManager.getCurrentUser().drawable[AbstractDungeon.actNum-1];
				if (c.pointQueue.size() == 0) { break; }

				Vector2[] points = c.pointQueue.remove(0);
				float xSize = c.pixmap.getWidth();
				float ySize = c.pixmap.getHeight();

				data.putFloat(4, points[0].x / xSize);
				data.putFloat(8, points[0].y / ySize);

				if (points[1] != null) {
					data.putFloat(12, points[1].x / xSize);
					data.putFloat(16, points[1].y / ySize);
				} else {
					data.putFloat(12, 0f);
					data.putFloat(16, 0f);
				}

				data.putFloat(20, c.brushSize);
				data.putFloat(24, DungeonMapScreen.offsetY / ySize);
				break;
			case ClearMap:
				data = ByteBuffer.allocateDirect(4);
				break;
			case DeckInfo:
				String deckCard = SendDataPatches.sendCard.cardID;

				// Deck Stats.
				data = ByteBuffer.allocateDirect(28 + deckCard.getBytes().length);

				data.putInt(4, AbstractDungeon.player.masterDeck.size());

				int upgraded = 0;
			    for (AbstractCard cup : AbstractDungeon.player.masterDeck.group) {
			    	upgraded += cup.timesUpgraded; 
			    } 

   				data.putInt(8, upgraded);
				((Buffer)data).position(12);

				// Card Update Stats
				data.putInt(12, SendDataPatches.sendCard.timesUpgraded);
				data.putInt(16, SendDataPatches.sendCard.misc);
				data.putInt(20, SendDataPatches.sendUpdate ? 1 : 0);
				data.putInt(24, SendDataPatches.sendRemove ? 1 : 0);

				((Buffer)data).position(28);
				data.put(deckCard.getBytes());
				((Buffer)data).rewind();

				SendDataPatches.sendCard = null;
				SendDataPatches.sendUpdate = false;
				SendDataPatches.sendRemove = false;
				break;
			case RelicInfo:
				data = ByteBuffer.allocateDirect(8);
				data.putInt(4, AbstractDungeon.player.relics.size());
				break;
			case RequestVersion:
				data = ByteBuffer.allocateDirect(4);
				break;
			case SendCardMessageBottle:
				String messageCard = MessageInABottle.sendCard.cardID;

				data = ByteBuffer.allocateDirect(12 + messageCard.getBytes().length);

				data.putInt(4, MessageInABottle.sendCard.timesUpgraded);
				data.putInt(8, MessageInABottle.sendCard.misc);

				((Buffer)data).position(12);
				data.put(messageCard.getBytes());
				((Buffer)data).rewind();

				MessageInABottle.sendCard = null; 
				break;				
			default:
				data = ByteBuffer.allocateDirect(4);
				break;
		}

		data.putInt(0, type.ordinal());

		return data;
	}

	public static Integration service() {
		if (TogetherManager.currentLobby == null) { 
			return null; 
		}

		if (TogetherManager.currentLobby.service == null) {
			return null; 
		}

		return TogetherManager.currentLobby.service;
	}

	public static void updateLobbyData() {
		if (TogetherManager.currentLobby != null) {
			Map<String,String> metadata = new HashMap();

			metadata.put("mode", TogetherManager.gameMode.toString());
			metadata.put("ascension", Integer.toString(NewMenuButtons.newGameScreen.ascensionSelectWidget.ascensionLevel));
			metadata.put("character", NewMenuButtons.newGameScreen.characterSelectWidget.getChosenOptionName());
			metadata.put("heart",   Boolean.toString(NewMenuButtons.newGameScreen.heartToggle.isTicked()));
			metadata.put("neow",    Boolean.toString(NewMenuButtons.newGameScreen.neowToggle.isTicked()));
			metadata.put("ironman", Boolean.toString(NewMenuButtons.newGameScreen.ironmanToggle.isTicked()));
			metadata.put("owner", TogetherManager.currentUser.userName);
			metadata.put("members", TogetherManager.currentLobby.getMemberNameList());

			TogetherManager.currentLobby.setMetadata(metadata);
		}
	}

	public static void createLobby(Integration service) {
		TogetherManager.log("Creating Lobby...");
		service.createLobby(TogetherManager.gameMode);
	}

	public static void setLobbyPrivate(boolean toggle) {
		TogetherManager.currentLobby.setPrivate(toggle);
	}

	public static void leaveLobby(){
		if (TogetherManager.currentLobby != null) {

			// Handle Ownership transfer
        	if (TogetherManager.currentLobby.isOwner())
        		TogetherManager.currentLobby.newOwner();

			// Leave Lobby
	        CardCrawlGame.mainMenuScreen.screen = MainMenuScreen.CurScreen.MAIN_MENU;
    	    CardCrawlGame.mainMenuScreen.lighten();

    	    TogetherManager.currentLobby.leaveLobby();
			TogetherManager.clearMultiplayerData();
		}
	}

	public static void getLobbies() {
	    NetworkHelper.lobbies.clear();

	    for (Integration service : networks)
			service.getLobbies();
	}

	public static void addPlayer(RemotePlayer player) {
		// Make sure we're not adding a dupe
		for (RemotePlayer oldplayer : TogetherManager.players)
			if (oldplayer.isUser(player))
				return;

        TogetherManager.players.add(player);
        TopPanelPlayerPanels.playerWidgets.add(new RemotePlayerWidget(player));
		
		TogetherManager.log("Member joined: " + player.userName);
	}

	public static void removePlayer(RemotePlayer player) {
		if (player == null) { return; }

		if (player.isUser(TogetherManager.currentUser) && CardCrawlGame.isInARun())
			TogetherManager.infoPopup.show(CardCrawlGame.languagePack.getUIString("Network").TEXT[0], CardCrawlGame.languagePack.getUIString("Network").TEXT[2]);

		if (embarked && TogetherManager.gameMode == TogetherManager.mode.Versus) {
			player.connection = false;
		} else {
			// Unlocks a room if a player disconnects.
			if (embarked) {
				int xc = player.x;
				int yc = player.y;
				if (xc != -1 && yc != -1 && yc < 16 && !AbstractDungeon.id.equals("TheEnding")) {
		            MapRoomNode currentNodec = AbstractDungeon.map.get(yc).get(xc);

		            // Safety first? This triggers if games are desynced, but I hate getting reports about it.
		            if (currentNodec == null) 			{ return; }
		            if (currentNodec.getRoom() == null) { return; }

		            // Unlocks a room we are leaving
					CoopEmptyRoom.LockedRoomField.locked.set(currentNodec.getRoom(), false);
				
					// Sets the next room of a multi-room
					AbstractRoom secondRoom = CoopMultiRoom.secondRoomField.secondRoom.get(currentNodec);
					AbstractRoom thirdRoom  = CoopMultiRoom.thirdRoomField.thirdRoom.get(currentNodec);

					// Resolve the multinodes by advancing the 'queue' 
					currentNodec.room = secondRoom;
					CoopMultiRoom.secondRoomField.secondRoom.set(currentNodec, thirdRoom);
					CoopMultiRoom.thirdRoomField.thirdRoom.set(currentNodec, null);

					if (currentNodec.room == null)
						currentNodec.setRoom(new CoopEmptyRoom());
				}
			}

			// Remove from player list
			TogetherManager.players.remove(player);
    		TogetherManager.log("Member left: " + player.userName);

			// Remove the widget
	        TopPanelPlayerPanels.playerWidgets.remove(player.widget);
		}

		int connected = 0;
		for (RemotePlayer np : TogetherManager.players) {
			if (np.connection) { connected++; }
		}

		if ((TogetherManager.players.size() <= 1 || connected <= 1) && CardCrawlGame.isInARun()) {
			TogetherManager.infoPopup.show(CardCrawlGame.languagePack.getUIString("Network").TEXT[3], CardCrawlGame.languagePack.getUIString("Network").TEXT[4]);
		}
	}
}