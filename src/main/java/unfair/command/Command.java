package unfair.command;

import java.util.ArrayList;

public abstract class Command {
    public final ArrayList<String> names;

    public Command(ArrayList<String> arrayList) {
        this.names = arrayList;
    }

    public abstract void runCommand(ArrayList<String> args);
}
