package unfair.management;

import unfair.enums.ChatColors;

import java.awt.*;
import java.io.File;

public class FriendManager extends PlayerFileManager {
    public FriendManager() {
        super(new File("./config/Unfair/", "friends.txt"), new Color(ChatColors.DARK_GREEN.toAwtColor()));
    }
}
