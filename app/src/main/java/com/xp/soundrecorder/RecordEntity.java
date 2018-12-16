package com.xp.soundrecorder;

import java.io.File;

public class RecordEntity {
    private File path;
    private String name;

    public RecordEntity(File path, String name) {
        this.path = path;
        this.name = name;
    }

    public File getPath() {
        return path;
    }

    public void setPath(File path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
