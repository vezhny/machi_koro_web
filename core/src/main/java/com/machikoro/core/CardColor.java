package com.machikoro.core;

/**
 * Establishment color: determines whose roll activates the card and in which order it resolves.
 */
public enum CardColor {
    /** Activates on anyone's roll; income from the bank. */
    BLUE,
    /** Activates only on the owner's roll; income from the bank. */
    GREEN,
    /** Activates on opponents' rolls; takes coins from the active player. */
    RED,
    /** Activates only on the owner's roll; major effects. At most one of each per player. */
    PURPLE
}
