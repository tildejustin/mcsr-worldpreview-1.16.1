package me.voidxwalker.worldpreview.compat;

import dev.tildejustin.stateoutput.State;
import dev.tildejustin.stateoutput.StateOutputHelper;

public class StateOutputCompat {

    public static void outputPreviewing() {
        // change the state to previewing, without changing the static progress
        StateOutputHelper.outputState(State.PREVIEW);
    }
}
