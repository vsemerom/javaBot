package ru.example.TelegramBotDemo.service;

import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScope;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeAllGroupChats;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeAllPrivateChats;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.example.TelegramBotDemo.config.BotConfig;
import ru.example.TelegramBotDemo.model.Data;
import ru.example.TelegramBotDemo.model.DataRepository;
import ru.example.TelegramBotDemo.model.User;
import ru.example.TelegramBotDemo.model.UserRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    // TODO:
    //  Разобраться с тем, как это всё захостить
    //  Добавить возможность удаления своих дюнных конкретным пользователем
    //  Добавить возможность просмотра отправленных данных конкретным пользователем (с номерами при выводе)
    //  Добавить поддержку ввода вида "12 100.00"


    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DataRepository dataRepository;
    final BotConfig config;
    static final String HELP_TEXT = """
            HELP
            
            Для добавления информации в бота, сообщение должно иметь вид:
            #тег число или #тег число + число +...
            Так же допускается написания нескольких пар #тег число в одном сообщении (!без сложения!)
            
            Список команд:
            /start - Первичная команда для инициализации бота. (только в личных сообщениях)
            /help - Выводит данное сообщение.
            """;

    static final String HELP_TEXT_ADMIN = """
            HELP (ДЛЯ АДМИНИСТРАТОРОВ)
            
            Для добавления информации в бота, сообщение должно иметь вид:
            #тег число или #тег число + число +...
            Так же допускается написания нескольких пар #тег число в одном сообщении (!без сложения!)
            
            Список команд:
            /start - Первичная команда для инициализации бота. (только в личных сообщениях)
            /get_excel - Позволяет получить .xlsx файл с данными из чата. (для администраторов, только в личных сообщениях)
            /help - Выводит данное сообщение.
            """;
    static final List<Long> TARGET_CHAT_LIST = List.of(-4084709624L); // id группы всегда отрицательно
    static final List<Long> ADMIN_LIST = List.of(361100723L, 119361935L); // Список id всех администраторов
    static final String ERROR_TEXT = "Произошла ошибка: ";
    String dateForSending = ""; // переменная нужна для корректного создания и обработки имён файлов
    DecimalFormat df = new DecimalFormat("#,###.00");

    public TelegramBot(BotConfig config) {
        super(config.getToken());
        this.config = config;
        List<BotCommand> listOfPrivateChatCommands = new ArrayList<>();
        listOfPrivateChatCommands.add(new BotCommand("/start", "Начать взаимодействие с ботом"));
        listOfPrivateChatCommands.add(new BotCommand("/get_excel", "Получить .xlsx файл"));
        listOfPrivateChatCommands.add(new BotCommand("/help", "Подробное описание бота"));
        try{
            this.execute(new SetMyCommands(listOfPrivateChatCommands, new BotCommandScopeAllPrivateChats(), null));
            log.info("Установлен список команд бота для PrivateChat");
        }
        catch (TelegramApiException e){
            log.error("Ошибка при настройке списка команд бота для ScopeAllPrivateChats: " + e.getMessage());
        }

        List<BotCommand> listOfGroupChatCommands = new ArrayList<>();
        listOfGroupChatCommands.add(new BotCommand("/help", "Подробное описание бота"));
        try{
            this.execute(new SetMyCommands(listOfGroupChatCommands, new BotCommandScopeAllGroupChats(), null));
            log.info("Установлен список команд бота для ScopeAllGroupChats");
        }
        catch (TelegramApiException e){
            log.error("Ошибка при настройке списка команд бота для GroupChat: " + e.getMessage());
        }

    }
    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public void onUpdateReceived(Update update) {
        long chatId = update.getMessage().getChatId();
        if(update.hasMessage() && update.getMessage().hasText() && (TARGET_CHAT_LIST.contains(chatId) || ADMIN_LIST.contains(chatId))){
            String messageText = update.getMessage().getText();

            if(ADMIN_LIST.contains(chatId)){ // только в лс у администраторов
                switch (messageText){
                    case "/get_excel":
                        var db = dataRepository.findAll();
                        createExcelSheet(db);
                        sendExcelFile(chatId);
                        break;
                    case "/help":
                        prepareAndSendMessage(chatId, HELP_TEXT_ADMIN, true);
                }
            }
            if(Objects.equals(messageText, "/start") && chatId > 0){ // только в лс пользователей
                registerUser(update.getMessage());
                startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                log.info("/start вызвана\nChatId : " + chatId);
            }
            else if (messageText.startsWith("#")) { // везде
                var pairs = AddPairToDb(update.getMessage());
                for (var pair : pairs ){
                    prepareAndSendMessage(chatId, "Запись [ " + pair.tag+ " : " + df.format(pair.value) + " ] добавлена", true);
                    log.info(String.format("Запись [ %s : %s ] добавлена", pair.tag, df.format(pair.value)));
                }
            }
            else {
                switch (messageText) {
                    case "/help", "/help@xyzrodi1_bot":
                        if(!ADMIN_LIST.contains(chatId)) {
                            prepareAndSendMessage(chatId, HELP_TEXT, true);
                        }
                        break;
                    default:
                        log.info(String.format("Сообщение от пользователя %d (%s): %s", chatId, update.getMessage().getFrom().getUserName(), messageText));
                        break;
                }
            }
        }
    }
    private void registerUser(Message msg) {
        if(userRepository.findById(msg.getChatId()).isEmpty()){
            var chatId = msg.getChatId();
            var chat = msg.getChat();

            User user = new User();
            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));

            userRepository.save(user);
            log.info(String.format("Пользователь %s сохранён ", user));
        }

    }

    private List<TagValuePair> AddPairToDb(Message msg){
        List<TagValuePair> TagValuePairs = analyzeMessage(msg.getText()); // пара тег - сумма

        for(TagValuePair pair : TagValuePairs){
            Data data = new Data();
            data.setMessageTime(new Timestamp(System.currentTimeMillis()));
            data.setMessageId(msg.getMessageId());
            data.setChatId(msg.getChatId());

            data.setUserId(msg.getFrom().getId());
            data.setFirstName(msg.getFrom().getFirstName());
            data.setLastName(msg.getFrom().getLastName());
            data.setUserName("@"+msg.getFrom().getUserName());

            data.setTag(pair.tag);
            data.setValue(pair.value);

            saveToDataBase(data);
            log.info(String.format("Пара %s сохранена", data));
        }

        return TagValuePairs;
    }

    @Transactional
    private void saveToDataBase(Data data){
        dataRepository.save(data);
    }
    private void startCommandReceived(long chatId, String name){

        String answer = EmojiParser.parseToUnicode("Здравствуйте, " + name + ", вы были успешно зарегистрированы.\nХорошего дня!" + ":blush:");
        log.info(String.format("Выполнен ответ пользователю %s на команду /start", name));

        sendStartMessage(chatId, answer);
    }

    private void sendStartMessage(long chatId, String textToSend){
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        executeMessage(message);
    }

    private void executeMessage(SendMessage message){
        try{
            execute(message);
        }
        catch (TelegramApiException e){
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private void prepareAndSendMessage(long chatId, String textToSend, boolean removeKeyboard){
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        if(removeKeyboard){
            ReplyKeyboardRemove removedKeyboard = new ReplyKeyboardRemove(true);
            message.setReplyMarkup(removedKeyboard);
            executeMessage(message);
            log.info(String.format("Клавиатура в чате %d удалена", chatId));
        }
        else executeMessage(message);

    }


    private List<TagValuePair> analyzeMessage(String messageText){
        List<TagValuePair> tagValuePairs = new ArrayList<>();
        String[] words = messageText.split("\\s+");

        String currentTag = null;
        double totalValue = 0.0;

        for (String word : words) {
            if (word.startsWith("#")) {
                if (currentTag != null) {
                    tagValuePairs.add(new TagValuePair(currentTag, totalValue));
                    currentTag = null;
                    totalValue = 0.0;
                }
                currentTag = word.substring(1);
            } else if (word.matches("\\d+(\\.\\d+)?")) {
                double value = Double.parseDouble(word);
                totalValue += value;
            }
        }

        if (currentTag != null) {
            tagValuePairs.add(new TagValuePair(currentTag, totalValue));
        }

        return tagValuePairs;
    }

    private void createExcelSheet(Iterable<Data> dbData){

        try (Workbook workbook = new XSSFWorkbook()){
            Sheet sheet = workbook.createSheet("My Table");
            CreationHelper createHelper = workbook.getCreationHelper();


            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("Pair Id");
            titleRow.createCell(1).setCellValue("Tag");
            titleRow.createCell(2).setCellValue("Value");
            titleRow.createCell(3).setCellValue("First Name");
            titleRow.createCell(4).setCellValue("Last Name");
            titleRow.createCell(5).setCellValue("User Name");
            titleRow.createCell(6).setCellValue("Date");
            titleRow.createCell(7).setCellValue("Message Id");
            titleRow.createCell(8).setCellValue("User Id");


            int rownum = 1;
            for (var dbDataSample : dbData) {
                Row row = sheet.createRow(rownum++);
                row.createCell(0).setCellValue(dbDataSample.getPairId());
                row.createCell(1).setCellValue(dbDataSample.getTag());
                row.createCell(2).setCellValue(dbDataSample.getValue());
                row.createCell(3).setCellValue(dbDataSample.getFirstName());
                row.createCell(4).setCellValue(dbDataSample.getLastName());
                row.createCell(5).setCellValue(dbDataSample.getUserName());

                Cell cell = row.createCell(6);
                CellStyle cellStyle = workbook.createCellStyle();
                cellStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy/MM/dd hh:mm"));
                cell.setCellValue(dbDataSample.getMessageTime());
                cell.setCellStyle(cellStyle);

                row.createCell(7).setCellValue(dbDataSample.getMessageId());
                row.createCell(8).setCellValue(dbDataSample.getUserId());
            }

            File currDir = new File(".");
            String path = currDir.getAbsolutePath();
            dateForSending = getCurrentDate();
            String fileLocation = path.substring(0, path.length() - 1) + dateForSending + "_records.xlsx";
            log.info("файл создан: " + fileLocation);

            try (FileOutputStream fileOut = new FileOutputStream(fileLocation)) {
                workbook.write(fileOut);
                workbook.close();
            }
            catch (Exception e){
                log.error(ERROR_TEXT + e.getMessage());
            }
        }
        catch (Exception e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private void sendExcelFile(Long chatId){
        SendDocument sendFile = new SendDocument();
        sendFile.setChatId(chatId);
        sendFile.setDocument(new InputFile(new File(dateForSending + "_records.xlsx")));
        try {
            execute(sendFile);
        } catch (Exception e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
        dateForSending = "";
    }
    private String getCurrentDate(){
        DateTimeFormatter date = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
        return date.format(LocalDateTime.now());
    }

//    private void makeCommands(BotCommandScope scope){
//
//    }

    record TagValuePair(String tag, double value) { }
}

