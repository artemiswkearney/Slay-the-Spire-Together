package chronoMods.ui.hud;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.*;
import com.badlogic.gdx.math.*;

import com.megacrit.cardcrawl.core.*;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.integrations.steam.*;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.relics.*;
import com.megacrit.cardcrawl.cards.*;
import com.megacrit.cardcrawl.screens.runHistory.*;
import com.codedisaster.steamworks.*;
import com.megacrit.cardcrawl.helpers.input.InputHelper;

import java.util.*;
import java.nio.*;

import chronoMods.*;
import chronoMods.network.steam.*;
import chronoMods.network.*;
import chronoMods.ui.deathScreen.*;
import chronoMods.ui.hud.*;
import chronoMods.ui.lobby.*;
import chronoMods.ui.mainMenu.*;
import chronoMods.utilities.*;

public class RemotePlayerWidget implements Comparable
{
	// Widget displaying the player status of the remote players

	// Ranking
	public int rank = 0;

	// Position
	public float x = -8.0F;
	public float y = 0.0F;

	// For interpolation effects
	public float sx = 0.0F;
	public float sy = 0.0F;

	public float dx = 0.0F;
	public float dy = 0.0F;

	// For shuffling them around the screen
	public float xoffset = 0.0F;
	public float yoffset = 0.0F;


	public float duration;
	public float standardDuration = 1.5f;

	private static final float ICON_W = 36f * Settings.scale;

	public RemotePlayer player;
	public Color displayColour = Color.WHITE.cpy();

	public Hitbox connectbox = new Hitbox(300f * Settings.scale, 64f * Settings.scale);
	public float hoverScale = 1.0f;

	public ArrayList<TinyCard> cards = new ArrayList<>();

	private static final float SHADOW_DIST_Y = 14.0F * Settings.scale;
	private static final float SHADOW_DIST_X = 9.0F * Settings.scale;
	private static final float BOX_EDGE_H = 32.0F * Settings.scale;
	private static final float BOX_BODY_H = 64.0F * Settings.scale;
	private static final float BOX_W = 320.0F * Settings.scale;


	public RemotePlayerWidget(RemotePlayer player) {
		this.player = player;
		this.player.widget = this;

		// Set the rank
		setRank(TopPanelPlayerPanels.playerWidgets.size());
	}

	// Sets the position for lerping.
	public void setPos(float x, float y) {
		this.sx = this.x;
		this.sy = this.y;

		this.dx = x;
		this.dy = y;

		if (this.duration <= 0f)
			this.duration = standardDuration;
	}

	// Sets the rank in the list, and from that determines the destination position.
	public void setRank(int rank) {
			TogetherManager.log("Setting rank to " + rank + " for " + player.userName);
			player.ranking = rank;

		// if (this.rank != rank) 
		// 	CardCrawlGame.sound.playV("APPEAR", 0.5F);

		this.rank = rank;

		setPos(-8.0F * Settings.scale, 760.0F * Settings.scale - 80.0F * rank * Settings.scale);
	}

	// Comparators for sorting, returns negative, 0, or positive for lower than, equal to, or higher than respectively
	@Override
	public int compareTo(Object compareToMe) {
			// These should be sorted in ascending order, first by finished time if available, then by floor
		RemotePlayerWidget c = (RemotePlayerWidget)compareToMe;

		// We've both completed the run
		if (player.finalTime > 0.0F && c.player.finalTime > 0.0F) {
			TogetherManager.log("Compared by final time");
			return (int)(c.player.finalTime - player.finalTime);
		}
		// We're not done but he is
		else if (player.finalTime == 0.0F && c.player.finalTime > 0.0F) {
			TogetherManager.log("We're not done");
			return -1;
		}
		// He's done but we're not
		else if (c.player.finalTime == 0.0F && player.finalTime > 0.0F) {
			TogetherManager.log("They're not done");
			return 1;
		}

		// Neither of us are done
		TogetherManager.log("Floor comparison! " + player.floor + " - " + c.player.floor);
		return player.floor - c.player.floor;
	}

	@Override
	public String toString() {
			return "Remote Player: " + player.userName + " @ Rank " + rank;
	}

	// Creates the Tiny Card array from the deck.
	public void updateCardDisplay() {

		// Make an exclusion set
		ArrayList<String> names = new ArrayList();

		// Sort the Deck
		player.deck.sortAlphabetically(true);
		player.deck.sortByRarityPlusStatusCardType(false);
		player.deck = player.deck.getGroupedByColor();

		// Add the TinyCards to the display list
		this.cards.clear();
		for (AbstractCard card : player.deck.group) {
			if (!names.contains(card.name)) {
				this.cards.add(new TinyCard(card, (int)player.deck.group.stream().filter(c -> c.cardID == card.cardID && c.timesUpgraded == card.timesUpgraded).count())); 
				names.add(card.name);
			}
		}

		// Layout Code
		float height = (this.cards.size() - 1) * screenPosY(48.0F);
		float originX = x + connectbox.width + screenPosX(150.0F);
		float originY = y + (height / 2.0f);
		float rowHeight = screenPosY(48.0F);
		float columnWidth = screenPosX(340.0F);
		
		if (originY > Settings.HEIGHT - screenPosY(128f))
			originY = Settings.HEIGHT - screenPosY(128f);

		// Column separation
		int row = 0, column = 0;
		// TinyCard.desiredColumns = (cards.size() <= 36) ? 3 : 4;
		TinyCard.desiredColumns = 1;
		int cardsPerColumn = cards.size() / TinyCard.desiredColumns;
		int remainderCards = cards.size() - cardsPerColumn * TinyCard.desiredColumns;
		int[] columnSizes = new int[TinyCard.desiredColumns];
		Arrays.fill(columnSizes, cardsPerColumn);

		for (int i = 0; i < remainderCards; i++)
			columnSizes[i % TinyCard.desiredColumns] = columnSizes[i % TinyCard.desiredColumns] + 1; 

		for (TinyCard card : cards) {
			if (row >= columnSizes[column]) {
				row = 0;
				column++;
			} 

			float cardY = originY - row * rowHeight;
			card.hb.move(originX + column * columnWidth + card.hb.width / 2.0F, cardY);

			if (card.col == -1) {
				card.col = column;
				card.row = row;
			} 

			row++;
		}
  	}

  	// Convenience Math
	private float screenPos(float val)  { return val * Settings.scale;  }
	private float screenPosX(float val) { return val * Settings.xScale; }
	private float screenPosY(float val) { return val * Settings.yScale; }


	// Render the widgets here
	public void render(SpriteBatch sb) { 

	// These babies don't update, so we'll do the lerping here.
		if (this.duration > 0.0F) {
			this.x = Interpolation.exp10Out.apply(this.dx, this.sx, this.duration);
			this.y = Interpolation.exp10Out.apply(this.dy, this.sy, this.duration);

			this.duration -= Gdx.graphics.getDeltaTime(); 

			// When animation is complete
			if (this.duration <= 0)
				updateCardDisplay();
		} else {
			this.duration = 0.0f;
		}

		float xn = this.x + this.xoffset;
		float yn = this.y + this.yoffset;

		displayColour.a = Math.max(0f, Math.min(1.0f, (yn - 190F * Settings.yScale) / (300.0F * Settings.yScale)));
		Color textColour = Settings.CREAM_COLOR.cpy().sub(0f,0f,0f,1.0f-displayColour.a);
		Color redTextColour = Settings.RED_TEXT_COLOR.cpy().sub(0f,0f,0f,1.0f-displayColour.a);
		Color goldTextColour = Settings.GOLD_COLOR.cpy().sub(0f,0f,0f,1.0f-displayColour.a);

		connectbox.update();
		connectbox.move(xn + TogetherManager.panelImg.getWidth() * Settings.scale / 2f, yn + TogetherManager.panelImg.getHeight() * Settings.scale / 2f);
		if (connectbox.hovered){
			hoverScale = 1.1f;
			if (InputHelper.justClickedLeft) {
				TogetherManager.currentLobby.service.messageUser(player);
				CardCrawlGame.sound.play("UI_CLICK_1");
			}
		} else {
				hoverScale = 1.0f;
		}

		// Drawing begins

		sb.setColor(displayColour);

		// Render Background
		// sb.draw(this.panelImg, this.x, this.y, 137.5F, 40.0F, 275.0F, 80.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 275, 80, false, false);
		sb.draw(TogetherManager.panelImg, xn, yn, TogetherManager.panelImg.getWidth() * Settings.scale, TogetherManager.panelImg.getHeight() * Settings.scale);

		// Draw the player colour indicator
		sb.setColor(player.colour);
		sb.draw(TogetherManager.colourIndicatorImg, xn, yn, TogetherManager.colourIndicatorImg.getWidth() * Settings.scale, TogetherManager.colourIndicatorImg.getHeight() * Settings.scale);
		sb.setColor(displayColour);

		// Render Portrait
		if (player.portraitImg != null) {
			sb.draw(player.portraitImg, xn + 26.0F * Settings.scale, yn+12.0F * Settings.scale, 56f * Settings.scale, 56f * Settings.scale);
		}

		// Render Portrait frame
			sb.draw(TogetherManager.portraitFrames.get(0), xn - 160.0F * Settings.scale, yn - 96.0F * Settings.scale, 0.0F, 0.0F, 432.0F, 243.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 1920, 1080, false, false);

		// Draw the user name
		FontHelper.renderSmartText(sb, player.useFallbackFont ? TogetherManager.fallbackFont : FontHelper.topPanelInfoFont, player.userName, xn + 96.0F * Settings.scale, yn + 64.0F * Settings.scale, Settings.WIDTH, 0.0F, textColour, hoverScale);

		// The player hasn't finished the run
		if (player.finalTime == 0.0F) {
			// Draw current floor
			sb.draw(ImageMaster.TP_FLOOR, xn + 88.0F * Settings.scale,  yn + 4.0F, ICON_W, ICON_W);
			FontHelper.renderSmartText(sb, FontHelper.cardDescFont_N, Integer.toString(player.floor), xn + 124.0F * Settings.scale,  yn + 32.0F * Settings.scale, textColour);

			if (player.connection) {
				// Draw HP
				sb.draw(ImageMaster.TP_HP,    xn + 164.0F * Settings.scale, yn + 4.0F, ICON_W, ICON_W);
				FontHelper.renderSmartText(sb, FontHelper.cardDescFont_N, Integer.toString(player.hp),    xn + 196.0F * Settings.scale,  yn + 32.0F * Settings.scale, redTextColour);

				// Draw Gold
				sb.draw(ImageMaster.TP_GOLD,  xn + 236.0F * Settings.scale, yn + 4.0F, ICON_W, ICON_W);
				FontHelper.renderSmartText(sb, FontHelper.cardDescFont_N, Integer.toString(player.gold),  xn + 272.0F * Settings.scale,  yn + 32.0F * Settings.scale, goldTextColour);
			} else {
				// Draw Disconnect
				sb.draw(TogetherManager.TP_WhiteHeart,    xn + 164.0F * Settings.scale, yn + 4.0F, ICON_W, ICON_W);
				FontHelper.renderSmartText(sb, FontHelper.cardDescFont_N, "Disconnected",    xn + 196.0F * Settings.scale,  yn + 32.0F * Settings.scale, redTextColour);
			}
		}
		// We've finished the run
		else {
			sb.draw(ImageMaster.TIMER_ICON, xn + 88.0F * Settings.scale, yn + 6.0F, ICON_W, ICON_W);
			FontHelper.renderSmartText(sb, FontHelper.cardDescFont_N, VersusTimer.returnTimeString(player.finalTime), xn + 124.0F * Settings.scale,  yn + 32.0F * Settings.scale, textColour);
		}

		// Render collected Boss relics
		Color.WHITE.a = displayColour.a;
		int i = 0;
		for (AbstractRelic r : player.displayRelics) {
			r.currentX = xn + screenPosX(280.0f) + screenPosX(64.0f) + screenPosX(i * 32.0f);
			r.currentY = yn + screenPosY(40f);
			r.render(sb);
			i++;
		}
		Color.WHITE.a = 1.0f;

		// Render the hover panel
		if (connectbox.hovered) {
			float height = (this.cards.size() - 1) * screenPosY(48.0F);
			float originY = y + (height / 2.0f);

			if (originY > Settings.HEIGHT - screenPosY(128f))
				originY = Settings.HEIGHT - screenPosY(128f);

			renderTipBox(sb, x + connectbox.width + screenPosX(150.0F) - screenPosX(20.0F), originY + screenPosY(20.0F), height);

		    for (TinyCard card : this.cards)
		      card.render(sb);
		}
	}

	private static void renderTipBox(SpriteBatch sb, float x, float y, float h) {
		// float h = textHeight;
		sb.setColor(Settings.TOP_PANEL_SHADOW_COLOR);
		sb.draw(ImageMaster.KEYWORD_TOP, x + SHADOW_DIST_X, y - SHADOW_DIST_Y, BOX_W, BOX_EDGE_H);
		sb.draw(ImageMaster.KEYWORD_BODY, x + SHADOW_DIST_X, y - h - BOX_EDGE_H - SHADOW_DIST_Y, BOX_W, h + BOX_EDGE_H);
		sb.draw(ImageMaster.KEYWORD_BOT, x + SHADOW_DIST_X, y - h - BOX_BODY_H - SHADOW_DIST_Y, BOX_W, BOX_EDGE_H);
		sb.setColor(Color.WHITE);
		sb.draw(ImageMaster.KEYWORD_TOP, x, y, BOX_W, BOX_EDGE_H);
		sb.draw(ImageMaster.KEYWORD_BODY, x, y - h - BOX_EDGE_H, BOX_W, h + BOX_EDGE_H);
		sb.draw(ImageMaster.KEYWORD_BOT, x, y - h - BOX_BODY_H, BOX_W, BOX_EDGE_H);
	}
}