package com.flowershop.flowershop.util;

import javax.swing.JTextField;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

public final class InputRules {

    private InputRules() {
    }

    public static void digitsOnly(JTextField field, int maxLength) {
        apply(field, "\\d*", maxLength);
    }

    public static void decimalOnly(JTextField field) {
        apply(field, "\\d*\\.?\\d*", 15);
    }

    public static void noSpaces(JTextField field) {
        apply(field, "\\S*", 100);
    }

    public static boolean isEmail(String text) {
        return text.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z]{2,})+$");
    }

    private static void apply(JTextField field, String regex, int maxLength) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr) throws BadLocationException {
                replace(fb, offset, 0, text, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                String current = fb.getDocument().getText(0, fb.getDocument().getLength());
                String added = text == null ? "" : text;
                String result = current.substring(0, offset) + added + current.substring(offset + length);
                if (result.length() <= maxLength && result.matches(regex)) {
                    super.replace(fb, offset, length, text, attrs);
                }
            }
        });
    }
}