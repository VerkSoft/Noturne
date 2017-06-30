package com.verksoft.noturne.ui;

/**
 * Created by esdras on 29/06/17.
 */

public interface EditTextInterface {
    public void init();
    public void show();
    public void hide();
    public String getString();
    public void updateTextFinger();
    public boolean isTextChanged();
}
