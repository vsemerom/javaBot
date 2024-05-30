package ru.example.TelegramBotDemo.config;

import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@Data
@PropertySource("application.properties")
public class BotConfig {

    @Value("${bot.name}")
    String botName;
    @Value("${TELEGRAM_BOT_TOKEN}")
    String token;
    @Value("${bot.owner}")
    Long ownerId;
}
