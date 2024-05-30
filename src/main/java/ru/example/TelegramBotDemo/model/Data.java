package ru.example.TelegramBotDemo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.sql.Timestamp;


@Entity(name="recordsData")
@lombok.Data
public class Data {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long pairId;

    private Integer messageId;
    private Timestamp messageTime;
    private Long chatId;
    private Long userId;
    private String firstName;
    private String lastName;
    private String userName;

    private String tag;
    private Double value;

    @Override
    public String toString() {
        return "Data{" +
                "messageId=" + messageId +
                ", messageAt=" + messageTime +
                ", userId=" + chatId +
                ", tag='" + tag + '\'' +
                ", value=" + value +
                '}';
    }
}
