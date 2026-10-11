package com.flowershop.flowershop;

import com.flowershop.flowershop.repository.UserRepository;
import com.flowershop.flowershop.ui.LoginFrame;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.HierarchyEvent;
import java.net.URL;

@SpringBootApplication
public class FlowershopApplication {

    private static final int MIN_SIZE = 15;   // smallest font size used anywhere
    private static final int ADD = 2;         // points added to smaller hard-coded fonts

    private static final long MIN_SPLASH_MS = 2500;             // splash stays at least this long
    private static final Color SPLASH_BG = new Color(2, 2, 14); // same as the logo background

    // 1) Standard controls without their own font get a readable default size
    private static void applyLargerDefaultFonts() {
        String[] plain = {"Label.font", "Button.font", "TextField.font", "PasswordField.font", "TextArea.font",
                "ComboBox.font", "Spinner.font", "FormattedTextField.font", "CheckBox.font", "RadioButton.font",
                "Table.font", "List.font", "OptionPane.messageFont", "OptionPane.buttonFont", "ToolTip.font",
                "TextPane.font", "EditorPane.font", "Menu.font", "MenuItem.font", "PopupMenu.font"};
        for (String key : plain) {
            UIManager.put(key, new FontUIResource("Segoe UI", Font.PLAIN, MIN_SIZE));
        }
        String[] bold = {"TableHeader.font", "TabbedPane.font", "TitledBorder.font"};
        for (String key : bold) {
            UIManager.put(key, new FontUIResource("Segoe UI", Font.BOLD, MIN_SIZE));
        }
    }

    // 2) Small hard-coded fonts (e.g. 12/13/14 pt) are bumped by a few points the first time
    //    the component is shown. Only the font size changes; no sizes or layouts are touched.
    private static void installFontBump() {
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (!(event instanceof HierarchyEvent he)) {
                return;
            }
            if ((he.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) {
                return;
            }
            Component c = he.getComponent();
            if (!(c instanceof JComponent jc) || jc.getClientProperty("fontBumped") != null) {
                return;
            }
            // HTML labels (word-wrapped hints) must not change font after layout, or they collapse
            if (jc instanceof JLabel lbl && lbl.getText() != null && lbl.getText().startsWith("<html>")) {
                return;
            }
            jc.putClientProperty("fontBumped", Boolean.TRUE);
            Font f = jc.getFont();
            if (f != null && f.getSize() < MIN_SIZE) {
                jc.setFont(f.deriveFont((float) (f.getSize() + ADD)));
            }
        }, AWTEvent.HIERARCHY_EVENT_MASK);
    }

    // 3) Splash screen: the logo on the same black as the sidebar, shown while the system starts up
    private static JWindow showSplash() throws Exception {
        JWindow[] holder = new JWindow[1];
        SwingUtilities.invokeAndWait(() -> {
            JWindow window = new JWindow();

            JPanel content = new JPanel(new BorderLayout(0, 18));
            content.setBackground(SPLASH_BG);
            content.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(60, 60, 66)),
                    BorderFactory.createEmptyBorder(32, 48, 28, 48)));

            JLabel logo = new JLabel("", SwingConstants.CENTER);
            URL url = FlowershopApplication.class.getResource("/static/logo.jpg");
            if (url != null) {
                Image scaled = new ImageIcon(url).getImage().getScaledInstance(280, -1, Image.SCALE_SMOOTH);
                logo.setIcon(new ImageIcon(scaled));
            } else {
                logo.setText("Junaie's Flower Shop");
                logo.setFont(new Font("Segoe UI", Font.BOLD, 26));
                logo.setForeground(Color.WHITE);
            }

            JLabel loading = new JLabel("Starting up...", SwingConstants.CENTER);
            loading.setFont(new Font("Segoe UI", Font.PLAIN, 15));
            loading.setForeground(new Color(190, 190, 196));

            JProgressBar bar = new JProgressBar();
            bar.setIndeterminate(true);
            bar.setBorderPainted(false);
            bar.setBackground(new Color(40, 40, 48));
            bar.setForeground(new Color(190, 190, 196));
            bar.setPreferredSize(new Dimension(280, 6));

            JPanel bottom = new JPanel(new BorderLayout(0, 10));
            bottom.setOpaque(false);
            bottom.add(loading, BorderLayout.NORTH);
            bottom.add(bar, BorderLayout.SOUTH);

            content.add(logo, BorderLayout.CENTER);
            content.add(bottom, BorderLayout.SOUTH);

            window.setContentPane(content);
            window.pack();
            window.setLocationRelativeTo(null);
            window.setAlwaysOnTop(true);
            window.setVisible(true);
            holder[0] = window;
        });
        return holder[0];
    }

    private static void closeSplash(JWindow splash) {
        if (splash != null) {
            SwingUtilities.invokeLater(splash::dispose);
        }
    }

    public static void main(String[] args) {
        applyLargerDefaultFonts();
        installFontBump();

        long started = System.currentTimeMillis();
        JWindow splash = null;
        try {
            splash = showSplash();
        } catch (Exception ignored) {
            // the app still starts without a splash
        }

        ConfigurableApplicationContext context;
        try {
            context = new SpringApplicationBuilder(FlowershopApplication.class)
                    .headless(false)
                    .run(args);
        } catch (RuntimeException ex) {
            closeSplash(splash);
            throw ex;
        }

        // Keep the splash visible for a moment even when startup is quick
        long remaining = MIN_SPLASH_MS - (System.currentTimeMillis() - started);
        if (remaining > 0) {
            try {
                Thread.sleep(remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        final JWindow splashWindow = splash;
        SwingUtilities.invokeLater(() -> {
            if (splashWindow != null) {
                splashWindow.dispose();
            }
            UserRepository userRepository = context.getBean(UserRepository.class);
            LoginFrame loginFrame = new LoginFrame(userRepository);
            loginFrame.setVisible(true);
        });
    }
}