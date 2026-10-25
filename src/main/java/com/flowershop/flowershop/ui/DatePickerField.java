package com.flowershop.flowershop.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DatePickerField extends JPanel {

    public enum Mode {
        DAY("Today"),
        WEEK("This week"),
        MONTH("This month");

        private final String shortcut;

        Mode(String shortcut) {
            this.shortcut = shortcut;
        }
    }

    private static final DateTimeFormatter DAY_TEXT = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter SHORT_DAY_TEXT = DateTimeFormatter.ofPattern("MMM dd");
    private static final DateTimeFormatter MONTH_TEXT = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final String[] WEEKDAYS = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};
    private static final Color ACCENT = new Color(13, 110, 253);
    private static final Color WEEK_TINT = new Color(221, 235, 255);
    private static final Color BORDER = new Color(230, 232, 236);

    private final JTextField txtDate = new JTextField(14);
    private final JButton btnOpen = new JButton("▼");
    private final JPopupMenu popup = new JPopupMenu();
    private final JLabel lblHeader = new JLabel("", SwingConstants.CENTER);
    private final JPanel grid = new JPanel();
    private final JButton btnPrevYear = navButton("«");
    private final JButton btnPrevMonth = navButton("‹");
    private final JButton btnNextMonth = navButton("›");
    private final JButton btnNextYear = navButton("»");
    private final JButton btnToday = new JButton();
    private final List<Runnable> listeners = new ArrayList<>();

    private Mode mode = Mode.DAY;
    private LocalDate date = LocalDate.now();
    private LocalDate minDate;
    private LocalDate maxDate;
    private YearMonth shown = YearMonth.from(date);

    public DatePickerField() {
        setLayout(new BorderLayout());
        setOpaque(false);

        txtDate.setEditable(false);
        txtDate.setBackground(Color.WHITE);
        txtDate.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        txtDate.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        btnOpen.setFocusPainted(false);
        btnOpen.setMargin(new Insets(2, 6, 2, 6));
        btnOpen.setFont(new Font("Segoe UI", Font.PLAIN, 10));

        add(txtDate, BorderLayout.CENTER);
        add(btnOpen, BorderLayout.EAST);

        popup.add(buildPopup());
        popup.setBorder(BorderFactory.createLineBorder(new Color(180, 184, 190)));

        btnOpen.addActionListener(e -> togglePopup());
        txtDate.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                togglePopup();
            }
        });

        refreshText();
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate newDate) {
        date = normalize(newDate);
        shown = YearMonth.from(date);
        refreshText();
    }

    public void setMode(Mode newMode) {
        mode = newMode;
        date = normalize(date);
        shown = YearMonth.from(date);
        btnToday.setText(mode.shortcut);
        refreshText();
    }

    public void setMinDate(LocalDate value) {
        minDate = value;
    }

    public void setMaxDate(LocalDate value) {
        maxDate = value;
    }

    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    private LocalDate normalize(LocalDate value) {
        switch (mode) {
            case WEEK:
                return value.with(DayOfWeek.MONDAY);
            case MONTH:
                return value.withDayOfMonth(1);
            default:
                return value;
        }
    }

    private void refreshText() {
        switch (mode) {
            case WEEK -> txtDate.setText(date.format(SHORT_DAY_TEXT) + " - " + date.plusDays(6).format(DAY_TEXT));
            case MONTH -> txtDate.setText(date.format(MONTH_TEXT));
            default -> txtDate.setText(date.format(DAY_TEXT));
        }
    }

    private boolean allowed(LocalDate value) {
        return (maxDate == null || !value.isAfter(maxDate)) && (minDate == null || !value.isBefore(minDate));
    }

    private void togglePopup() {
        if (popup.isVisible()) {
            popup.setVisible(false);
            return;
        }
        shown = YearMonth.from(date);
        fillGrid();
        popup.show(this, 0, getHeight());
    }

    private JPanel buildPopup() {
        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        btnPrevYear.addActionListener(e -> move(-12));
        btnPrevMonth.addActionListener(e -> move(mode == Mode.MONTH ? -12 : -1));
        btnNextMonth.addActionListener(e -> move(mode == Mode.MONTH ? 12 : 1));
        btnNextYear.addActionListener(e -> move(12));

        lblHeader.setFont(new Font("Segoe UI", Font.BOLD, 14));

        JPanel left = new JPanel(new GridLayout(1, 2, 2, 0));
        left.setOpaque(false);
        left.add(btnPrevYear);
        left.add(btnPrevMonth);

        JPanel right = new JPanel(new GridLayout(1, 2, 2, 0));
        right.setOpaque(false);
        right.add(btnNextMonth);
        right.add(btnNextYear);

        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);
        header.add(left, BorderLayout.WEST);
        header.add(lblHeader, BorderLayout.CENTER);
        header.add(right, BorderLayout.EAST);

        grid.setOpaque(false);
        grid.setPreferredSize(new Dimension(266, 220));

        btnToday.setText(mode.shortcut);
        btnToday.setFocusPainted(false);
        btnToday.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnToday.addActionListener(e -> pick(LocalDate.now()));

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        footer.setOpaque(false);
        footer.add(btnToday);

        root.add(header, BorderLayout.NORTH);
        root.add(grid, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        return root;
    }

    private JButton navButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setMargin(new Insets(0, 6, 0, 6));
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        return button;
    }

    private void move(int months) {
        shown = shown.plusMonths(months);
        fillGrid();
    }

    private void fillGrid() {
        grid.removeAll();
        btnPrevYear.setVisible(mode != Mode.MONTH);
        btnNextYear.setVisible(mode != Mode.MONTH);
        btnToday.setEnabled(allowed(LocalDate.now()));

        if (mode == Mode.MONTH) {
            fillMonths();
        } else {
            fillDays();
        }

        grid.revalidate();
        grid.repaint();
    }

    private void fillMonths() {
        lblHeader.setText(String.valueOf(shown.getYear()));
        grid.setLayout(new GridLayout(4, 3, 4, 4));

        for (int i = 1; i <= 12; i++) {
            YearMonth value = YearMonth.of(shown.getYear(), i);
            JButton button = cellButton(Month.of(i).getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            boolean selected = value.equals(YearMonth.from(date));
            styleCell(button, selected, false, allowed(value.atDay(1)));
            button.addActionListener(e -> pick(value.atDay(1)));
            grid.add(button);
        }
    }

    private void fillDays() {
        lblHeader.setText(shown.format(MONTH_TEXT));
        grid.setLayout(new GridLayout(7, 7, 2, 2));

        for (String name : WEEKDAYS) {
            JLabel label = new JLabel(name, SwingConstants.CENTER);
            label.setFont(new Font("Segoe UI", Font.BOLD, 12));
            label.setForeground(new Color(110, 110, 110));
            grid.add(label);
        }

        int offset = shown.atDay(1).getDayOfWeek().getValue() - 1;
        LocalDate today = LocalDate.now();

        for (int cell = 0; cell < 42; cell++) {
            int day = cell - offset + 1;
            if (day < 1 || day > shown.lengthOfMonth()) {
                grid.add(new JLabel(""));
                continue;
            }
            LocalDate value = shown.atDay(day);
            JButton button = cellButton(String.valueOf(day));
            boolean selected = mode == Mode.WEEK ? value.with(DayOfWeek.MONDAY).equals(date) : value.equals(date);
            styleCell(button, selected && (mode == Mode.DAY), selected && mode == Mode.WEEK, allowed(value));
            if (value.equals(today) && !selected) {
                button.setBorder(BorderFactory.createLineBorder(ACCENT));
            }
            button.addActionListener(e -> pick(value));
            grid.add(button);
        }
    }

    private JButton cellButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(BorderFactory.createLineBorder(BORDER));
        return button;
    }

    private void styleCell(JButton button, boolean selected, boolean inWeek, boolean enabled) {
        button.setEnabled(enabled);
        if (selected) {
            button.setBackground(ACCENT);
            button.setForeground(Color.WHITE);
            button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        } else if (inWeek) {
            button.setBackground(WEEK_TINT);
            button.setForeground(Color.BLACK);
            button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        } else {
            button.setBackground(Color.WHITE);
            button.setForeground(enabled ? Color.BLACK : new Color(190, 190, 190));
        }
    }

    private void pick(LocalDate value) {
        if (!allowed(value)) {
            return;
        }
        setDate(value);
        popup.setVisible(false);
        for (Runnable listener : new ArrayList<>(listeners)) {
            listener.run();
        }
    }
}