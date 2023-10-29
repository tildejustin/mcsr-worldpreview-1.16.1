package me.voidxwalker.worldpreview.interfaces;

import java.io.IOException;

public interface FastCloseable {

    void worldpreview$fastClose() throws IOException;

    void worldpreview$setNewWorld();
}
