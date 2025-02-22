package com.divinity.hlspells.items.capabilities.spellholdercap;

import java.util.List;

public interface ISpellHolder {
    List<String> getSpells();

    /**
     * Do your own check to see if the spell can be added
     */
    void addSpell(String spell);

    void removeSpell(String spell);

    boolean containsSpell(String spell);

    int getCurrentSpellCycle();

    void setCurrentSpellCycle(int currentSpellCycle);

    String getCurrentSpell();

    boolean isHeldActive();

    void setHeldActive(boolean held);
}
