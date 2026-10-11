package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.service.BackupService;
import com.flowershop.flowershop.service.BackupService.BackupInfo;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;

public class BackupPanel extends JPanel {

    private static final int WEEKS_SHOWN = 12;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private record WeekOption(LocalDate start, boolean current) {
        @Override
        public String toString() {
            return start.format(DAY) + " - " + start.plusDays(6).format(DAY) + (current ? "  (this week)" : "");
        }
    }

    private final User currentUser;
    private final BackupService backupService;
    private final JTextField txtFolder = new JTextField();
    private final JComboBox<WeekOption> cmbWeek = new JComboBox<>();
    private final JButton btnBrowse = new JButton("Browse...");
    private final JButton btnWeekly = new JButton("Back Up Selected Week");
    private final JButton btnFull = new JButton("Full Backup");
    private final JButton btnOpenFolder = new JButton("Open Folder");
    private final JProgressBar progress = new JProgressBar();
    private final JLabel lblStatus = new JLabel(" ");
    private final DefaultTableModel model;

    public BackupPanel(User currentUser, BackupService backupService) {
        this.currentUser = currentUser;
        this.backupService = backupService;
        txtFolder.setText(backupService.getFolder().toString());

        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(245, 247, 250));

        JLabel lblTitle = new JLabel("Backups");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));

        JPanel controls = new JPanel(new GridBagLayout());
        controls.setBackground(Color.WHITE);
        controls.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.anchor = GridBagConstraints.WEST;

        gc.gridx = 0;
        gc.gridy = 0;
        controls.add(boldLabel("Backup folder"), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        controls.add(txtFolder, gc);
        gc.gridx = 2;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        controls.add(btnBrowse, gc);

        gc.gridx = 0;
        gc.gridy = 1;
        controls.add(boldLabel("Weekly (differential)"), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        controls.add(cmbWeek, gc);
        gc.gridx = 2;
        gc.fill = GridBagConstraints.NONE;
        controls.add(btnWeekly, gc);

        gc.gridx = 1;
        gc.gridy = 2;
        gc.gridwidth = 2;
        controls.add(hint("Only the data gathered during the selected week (Monday to Sunday), plus the shared "
                + "records (users, products, categories, components, recipes, suppliers)."), gc);

        gc.gridx = 0;
        gc.gridy = 3;
        gc.gridwidth = 1;
        controls.add(boldLabel("Full backup"), gc);
        gc.gridx = 2;
        controls.add(btnFull, gc);
        gc.gridx = 1;
        gc.gridy = 4;
        gc.gridwidth = 2;
        controls.add(hint("Every record in the system, regardless of which week it was gathered."), gc);

        gc.gridx = 0;
        gc.gridy = 5;
        gc.gridwidth = 1;
        controls.add(boldLabel("Automatic"), gc);
        gc.gridx = 1;
        gc.gridwidth = 2;
        controls.add(hint("Every Monday the previous week is backed up automatically into the folder above, "
                + "as long as the system is open. A week missed while the system was closed is backed up "
                + "the next time it starts."), gc);

        btnWeekly.setBackground(new Color(13, 110, 253));
        btnWeekly.setForeground(Color.WHITE);
        btnFull.setBackground(new Color(25, 135, 84));
        btnFull.setForeground(Color.WHITE);
        btnWeekly.setFocusPainted(false);
        btnFull.setFocusPainted(false);
        btnWeekly.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnFull.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(lblTitle, BorderLayout.NORTH);
        top.add(controls, BorderLayout.CENTER);

        model = new DefaultTableModel(new String[]{"Type", "Period", "Created", "Size", "File"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setRowHeight(28);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(4).setPreferredWidth(420);

        progress.setIndeterminate(true);
        progress.setVisible(false);
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JPanel statusBox = new JPanel(new BorderLayout(10, 0));
        statusBox.setOpaque(false);
        statusBox.add(lblStatus, BorderLayout.CENTER);
        statusBox.add(progress, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout(10, 0));
        bottom.setOpaque(false);
        bottom.add(statusBox, BorderLayout.CENTER);
        bottom.add(btnOpenFolder, BorderLayout.EAST);

        JLabel lblHistory = new JLabel("Backup history");
        lblHistory.setFont(new Font("Segoe UI", Font.BOLD, 15));

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(lblHistory, BorderLayout.NORTH);
        center.add(new JScrollPane(table), BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        btnBrowse.addActionListener(e -> chooseFolder());
        btnOpenFolder.addActionListener(e -> openFolder());
        btnWeekly.addActionListener(e -> {
            WeekOption week = (WeekOption) cmbWeek.getSelectedItem();
            if (week != null) {
                run("Weekly backup", () -> backupService.createWeeklyBackup(currentUser, week.start(), folder()));
            }
        });
        btnFull.addActionListener(e -> run("Full backup", () -> backupService.createFullBackup(currentUser, folder())));
        txtFolder.addActionListener(e -> {
            backupService.setFolder(folder());
            loadHistory();
        });

        loadData();
    }

    public void loadData() {
        loadWeeks();
        loadHistory();
    }

    private JLabel boldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        return label;
    }

    private JLabel hint(String text) {
        JLabel label = new JLabel("<html>" + text + "</html>");
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        label.setForeground(new Color(110, 110, 110));
        return label;
    }

    private Path folder() {
        String text = txtFolder.getText().trim();
        return Paths.get(text.isEmpty() ? System.getProperty("user.home") : text);
    }

    private void loadWeeks() {
        WeekOption selected = (WeekOption) cmbWeek.getSelectedItem();
        LocalDate thisWeek = BackupService.weekStartOf(LocalDate.now());

        cmbWeek.removeAllItems();
        WeekOption toSelect = null;
        for (int i = 0; i < WEEKS_SHOWN; i++) {
            WeekOption option = new WeekOption(thisWeek.minusWeeks(i), i == 0);
            cmbWeek.addItem(option);
            if (selected != null && selected.start().equals(option.start())) {
                toSelect = option;
            }
        }
        cmbWeek.setSelectedItem(toSelect != null ? toSelect : cmbWeek.getItemAt(0));
    }

    private void loadHistory() {
        model.setRowCount(0);
        for (BackupInfo info : backupService.listBackups(folder())) {
            model.addRow(new Object[]{
                    info.type(),
                    info.period(),
                    info.created().format(DATE_TIME),
                    formatSize(info.sizeBytes()),
                    info.file().getFileName().toString()
            });
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser(folder().toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose backup folder");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            txtFolder.setText(chooser.getSelectedFile().getAbsolutePath());
            backupService.setFolder(folder());
            loadHistory();
        }
    }

    private void openFolder() {
        try {
            java.io.File dir = folder().toFile();
            dir.mkdirs();
            Desktop.getDesktop().open(dir);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open the folder: " + ex.getMessage(),
                    "Backups", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setBusy(boolean busy) {
        btnWeekly.setEnabled(!busy);
        btnFull.setEnabled(!busy);
        btnBrowse.setEnabled(!busy);
        txtFolder.setEnabled(!busy);
        progress.setVisible(busy);
    }

    private void run(String label, Callable<Path> job) {
        backupService.setFolder(folder());
        setBusy(true);
        lblStatus.setText(label + " in progress...");
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return job.call();
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    Path file = get();
                    lblStatus.setText(label + " saved: " + file.getFileName());
                    loadHistory();
                    JOptionPane.showMessageDialog(BackupPanel.this,
                            label + " saved to:\n" + file.toAbsolutePath(),
                            "Backups", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    lblStatus.setText(label + " failed.");
                    JOptionPane.showMessageDialog(BackupPanel.this,
                            label + " failed: " + cause.getMessage(),
                            "Backups", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}