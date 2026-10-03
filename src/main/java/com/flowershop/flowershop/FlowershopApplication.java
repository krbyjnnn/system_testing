package com.flowershop.flowershop;

import com.flowershop.flowershop.repository.UserRepository;
import com.flowershop.flowershop.ui.LoginFrame;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.SwingUtilities;

@SpringBootApplication
public class FlowershopApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(FlowershopApplication.class)
                .headless(false)
                .run(args);

        SwingUtilities.invokeLater(() -> {
            UserRepository userRepository = context.getBean(UserRepository.class);
            LoginFrame loginFrame = new LoginFrame(userRepository);
            loginFrame.setVisible(true);
        });
    }
}