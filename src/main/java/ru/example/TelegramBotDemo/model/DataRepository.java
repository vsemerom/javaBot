package ru.example.TelegramBotDemo.model;

import org.springframework.data.repository.CrudRepository;

public interface DataRepository extends CrudRepository<Data, Long> {
}
