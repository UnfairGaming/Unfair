package net.minecraft.client.gui;

import cn.unfair.Unfair;
import cn.unfair.event.EventManager;
import cn.unfair.events.ChatGUIEvent;
import com.google.common.collect.Lists;
import net.minecraft.network.play.client.C14PacketTabComplete;
import net.minecraft.util.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class GuiChat extends GuiScreen {
    private static final Logger logger = LogManager.getLogger();
    /**
     * Chat entry field
     */
    protected GuiTextField inputField;
    private GuiTextField searchField;
    private boolean searchFocused;
    private boolean regexSearch;
    private String searchError;
    private String historyBuffer = "";
    /**
     * keeps position of which chat message you will select when you press up, (does not increase for duplicated
     * messages sent immediately after each other)
     */
    private int sentHistoryCursor = -1;
    private boolean playerNamesFound;
    private boolean waitingOnAutocomplete;
    private boolean clientAutocompleteFound;
    private int autocompleteIndex;
    private int clientAutocompleteStart;
    private List<String> foundPlayerNames = Lists.newArrayList();
    /**
     * is the text that appears when you press the chat key and the input box appears pre-filled
     */
    private String defaultInputFieldText = "";

    public GuiChat() {
    }

    public GuiChat(String defaultText) {
        this.defaultInputFieldText = defaultText;
    }

    /**
     * Adds the buttons (and other controls) to the screen in question. Called when the GUI is displayed and when the
     * window resizes, the buttonList is cleared beforehand.
     */
    public void initGui() {
        String previousSearchText = this.searchField == null ? "" : this.searchField.getText();
        boolean wasSearchFocused = this.searchFocused;
        Keyboard.enableRepeatEvents(true);
        this.sentHistoryCursor = this.mc.ingameGUI.getChatGUI().getSentMessages().size();
        this.inputField = new GuiTextField(0, this.fontRendererObj, 4, this.height - 12, this.width - 4, 12);
        this.inputField.setMaxStringLength(100);
        this.inputField.setEnableBackgroundDrawing(false);
        this.inputField.setFocused(true);
        this.inputField.setText(this.defaultInputFieldText);
        this.inputField.setCanLoseFocus(false);
        this.searchField = new GuiTextField(1, this.fontRendererObj, 4, this.height - 25, 96, 12);
        this.searchField.setMaxStringLength(256);
        this.searchField.setEnableBackgroundDrawing(true);
        this.searchField.setCanLoseFocus(false);
        this.searchField.setText(previousSearchText);

        if (wasSearchFocused) {
            this.setSearchFocus(true);
        }
    }

    public GuiTextField getInputField() {
        return this.inputField;
    }

    /**
     * Called when the screen is unloaded. Used to disable keyboard repeat events
     */
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        this.mc.ingameGUI.getChatGUI().resetScroll();
        this.mc.ingameGUI.getChatGUI().clearSearch();
    }

    /**
     * Called from the main game loop to update the screen.
     */
    public void updateScreen() {
        this.inputField.updateCursorCounter();
        this.searchField.updateCursorCounter();
    }

    /**
     * Fired when a key is typed (except F11 which toggles full screen). This is the equivalent of
     * KeyListener.keyTyped(KeyEvent e). Args : character (character on the key), keyCode (lwjgl Keyboard key code)
     */
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_F && isCtrlKeyDown()) {
            if (this.searchFocused && this.regexSearch == isShiftKeyDown()) {
                this.setSearchFocus(false);
            } else {
                this.regexSearch = isShiftKeyDown();
                this.setSearchFocus(true);
                this.updateSearch();
            }
            return;
        }

        if (this.searchFocused) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                this.setSearchFocus(false);
            } else {
                this.searchField.textboxKeyTyped(typedChar, keyCode);
                this.updateSearch();
            }
            return;
        }

        this.waitingOnAutocomplete = false;

        if (keyCode == 15) {
            if (!this.autocompleteClientCommand()) {
                this.autocompletePlayerNames();
            }
        } else {
            this.playerNamesFound = false;
            this.clientAutocompleteFound = false;
        }

        if (keyCode == 1) {
            this.mc.displayGuiScreen(null);
        } else if (keyCode != 28 && keyCode != 156) {
            if (keyCode == 200) {
                this.getSentHistory(-1);
            } else if (keyCode == 208) {
                this.getSentHistory(1);
            } else if (keyCode == 201) {
                this.mc.ingameGUI.getChatGUI().scroll(this.mc.ingameGUI.getChatGUI().getLineCount() - 1);
            } else if (keyCode == 209) {
                this.mc.ingameGUI.getChatGUI().scroll(-this.mc.ingameGUI.getChatGUI().getLineCount() + 1);
            } else {
                this.inputField.textboxKeyTyped(typedChar, keyCode);
            }
        } else {
            String s = this.inputField.getText().trim();

            if (!s.isEmpty()) {
                this.sendChatMessage(s);
            }

            this.mc.displayGuiScreen(null);
        }
    }

    private boolean autocompleteClientCommand() {
        if (Unfair.commandManager == null || !this.inputField.getText().startsWith(".")) {
            return false;
        }

        if (this.clientAutocompleteFound) {
            this.inputField.deleteFromCursor(this.clientAutocompleteStart - this.inputField.getCursorPosition());

            if (this.autocompleteIndex >= this.foundPlayerNames.size()) {
                this.autocompleteIndex = 0;
            }
        } else {
            String text = this.inputField.getText();
            int cursor = this.inputField.getCursorPosition();
            this.clientAutocompleteStart = Unfair.commandManager.getAutocompleteStart(text, cursor);
            this.foundPlayerNames.clear();
            this.autocompleteIndex = 0;
            this.foundPlayerNames.addAll(Unfair.commandManager.getAutocompleteSuggestions(text, cursor));

            if (this.foundPlayerNames.isEmpty()) {
                return true;
            }

            String current = text.substring(this.clientAutocompleteStart, cursor);
            String commonPrefix = StringUtils.getCommonPrefix(this.foundPlayerNames.toArray(new String[0]));

            if (!commonPrefix.isEmpty() && !current.equalsIgnoreCase(commonPrefix)) {
                this.inputField.deleteFromCursor(this.clientAutocompleteStart - this.inputField.getCursorPosition());
                this.inputField.writeText(commonPrefix);

                if (this.foundPlayerNames.size() == 1) {
                    this.inputField.writeText(" ");
                }
                return true;
            }

            this.clientAutocompleteFound = true;
            this.playerNamesFound = false;
            this.inputField.deleteFromCursor(this.clientAutocompleteStart - this.inputField.getCursorPosition());
        }

        if (this.foundPlayerNames.size() > 1) {
            StringBuilder stringbuilder = new StringBuilder();

            int shown = 0;
            for (String suggestion : this.foundPlayerNames) {
                if (shown >= 20) {
                    break;
                }

                if (!stringbuilder.isEmpty()) {
                    stringbuilder.append(", ");
                }

                stringbuilder.append(suggestion);
                shown++;
            }

            if (this.foundPlayerNames.size() > shown) {
                stringbuilder.append(", ... +").append(this.foundPlayerNames.size() - shown);
            }

            this.mc.ingameGUI.getChatGUI().printChatMessageWithOptionalDeletion(new ChatComponentText(stringbuilder.toString()), 1);
        }

        this.inputField.writeText(this.foundPlayerNames.get(this.autocompleteIndex++));

        if (this.foundPlayerNames.size() == 1) {
            this.inputField.writeText(" ");
        }

        return true;
    }

    /**
     * Handles mouse input.
     */
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int i = Mouse.getEventDWheel();

        if (i != 0) {
            if (i > 1) {
                i = 1;
            }

            if (i < -1) {
                i = -1;
            }

            if (!isShiftKeyDown()) {
                i *= 7;
            }

            this.mc.ingameGUI.getChatGUI().scroll(i);
        }
    }

    /**
     * Called when the mouse is clicked. Args : mouseX, mouseY, clickedButton
     */
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        boolean mouseOnSearchField = mouseX >= 4 && mouseX < 100 && mouseY >= this.height - 25 && mouseY < this.height - 13;

        if (this.searchFocused) {
            if (mouseButton == 0 && mouseOnSearchField) {
                this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
                return;
            }

            this.setSearchFocus(false);
        } else if (mouseButton == 0 && mouseOnSearchField) {
            this.setSearchFocus(true);
            this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (mouseButton == 0) {
            IChatComponent ichatcomponent = this.mc.ingameGUI.getChatGUI().getChatComponent(Mouse.getX(), Mouse.getY());

            if (this.handleComponentClick(ichatcomponent)) {
                return;
            }
        }

        this.inputField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    /**
     * Sets the text of the chat
     */
    protected void setText(String newChatText, boolean shouldOverwrite) {
        if (shouldOverwrite) {
            this.inputField.setText(newChatText);
        } else {
            this.inputField.writeText(newChatText);
        }
    }

    public void autocompletePlayerNames() {
        if (this.playerNamesFound) {
            this.inputField.deleteFromCursor(this.inputField.func_146197_a(-1, this.inputField.getCursorPosition(), false) - this.inputField.getCursorPosition());

            if (this.autocompleteIndex >= this.foundPlayerNames.size()) {
                this.autocompleteIndex = 0;
            }
        } else {
            int i = this.inputField.func_146197_a(-1, this.inputField.getCursorPosition(), false);
            this.foundPlayerNames.clear();
            this.autocompleteIndex = 0;
            String s = this.inputField.getText().substring(i).toLowerCase();
            String s1 = this.inputField.getText().substring(0, this.inputField.getCursorPosition());
            this.sendAutocompleteRequest(s1, s);

            if (this.foundPlayerNames.isEmpty()) {
                return;
            }

            this.playerNamesFound = true;
            this.inputField.deleteFromCursor(i - this.inputField.getCursorPosition());
        }

        if (this.foundPlayerNames.size() > 1) {
            StringBuilder stringbuilder = new StringBuilder();

            for (String s2 : this.foundPlayerNames) {
                if (!stringbuilder.isEmpty()) {
                    stringbuilder.append(", ");
                }

                stringbuilder.append(s2);
            }

            this.mc.ingameGUI.getChatGUI().printChatMessageWithOptionalDeletion(new ChatComponentText(stringbuilder.toString()), 1);
        }

        this.inputField.writeText(this.foundPlayerNames.get(this.autocompleteIndex++));
    }

    private void sendAutocompleteRequest(String p_146405_1_, String p_146405_2_) {
        if (!p_146405_1_.isEmpty()) {
            BlockPos blockpos = null;

            if (this.mc.objectMouseOver != null && this.mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                blockpos = this.mc.objectMouseOver.getBlockPos();
            }

            this.mc.thePlayer.sendQueue.addToSendQueue(new C14PacketTabComplete(p_146405_1_, blockpos));
            this.waitingOnAutocomplete = true;
        }
    }

    /**
     * input is relative and is applied directly to the sentHistoryCursor so -1 is the previous message, 1 is the next
     * message from the current cursor position
     */
    public void getSentHistory(int msgPos) {
        int i = this.sentHistoryCursor + msgPos;
        int j = this.mc.ingameGUI.getChatGUI().getSentMessages().size();
        i = MathHelper.clamp_int(i, 0, j);

        if (i != this.sentHistoryCursor) {
            if (i == j) {
                this.sentHistoryCursor = j;
                this.inputField.setText(this.historyBuffer);
            } else {
                if (this.sentHistoryCursor == j) {
                    this.historyBuffer = this.inputField.getText();
                }

                this.inputField.setText(this.mc.ingameGUI.getChatGUI().getSentMessages().get(i));
                this.sentHistoryCursor = i;
            }
        }
    }

    /**
     * Draws the screen and all the components in it. Args : mouseX, mouseY, renderPartialTicks
     */
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(2, this.height - 14, this.width - 2, this.height - 2, Integer.MIN_VALUE);
        this.inputField.drawTextBox();
        if (this.searchFocused) {
            this.searchField.drawTextBox();
            String label = this.regexSearch ? "Regex" : "Search";
            this.fontRendererObj.drawStringWithShadow(label, 104.0F, (float) (this.height - 23), this.searchError == null ? 10526880 : 16733525);
            if (this.searchError != null) {
                this.fontRendererObj.drawStringWithShadow(this.searchError, 104.0F, (float) (this.height - 14), 16733525);
            }
        }
        IChatComponent ichatcomponent = this.mc.ingameGUI.getChatGUI().getChatComponent(Mouse.getX(), Mouse.getY());

        if (ichatcomponent != null && ichatcomponent.getChatStyle().getChatHoverEvent() != null) {
            this.handleComponentHover(ichatcomponent, mouseX, mouseY);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
        EventManager.call(new ChatGUIEvent(mouseX, mouseY, partialTicks));
    }

    public void onAutocompleteResponse(String[] p_146406_1_) {
        if (this.waitingOnAutocomplete) {
            this.playerNamesFound = false;
            this.foundPlayerNames.clear();

            for (String s : p_146406_1_) {
                if (!s.isEmpty()) {
                    this.foundPlayerNames.add(s);
                }
            }

            String s1 = this.inputField.getText().substring(this.inputField.func_146197_a(-1, this.inputField.getCursorPosition(), false));
            String s2 = StringUtils.getCommonPrefix(p_146406_1_);

            if (!s2.isEmpty() && !s1.equalsIgnoreCase(s2)) {
                this.inputField.deleteFromCursor(this.inputField.func_146197_a(-1, this.inputField.getCursorPosition(), false) - this.inputField.getCursorPosition());
                this.inputField.writeText(s2);
            } else if (!this.foundPlayerNames.isEmpty()) {
                this.playerNamesFound = true;
                this.autocompletePlayerNames();
            }
        }
    }

    /**
     * Returns true if this GUI should pause the game when it is displayed in single-player
     */
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void setSearchFocus(boolean focused) {
        this.searchFocused = focused;
        this.searchField.setFocused(focused);
        this.inputField.setFocused(!focused);
    }

    private void updateSearch() {
        if (this.regexSearch) {
            try {
                this.mc.ingameGUI.getChatGUI().setSearchPattern(Pattern.compile(this.searchField.getText(), Pattern.CASE_INSENSITIVE));
                this.searchError = null;
            } catch (PatternSyntaxException exception) {
                this.searchError = exception.getDescription();
            }
        } else {
            this.mc.ingameGUI.getChatGUI().setSearchText(this.searchField.getText());
            this.searchError = null;
        }
    }
}
