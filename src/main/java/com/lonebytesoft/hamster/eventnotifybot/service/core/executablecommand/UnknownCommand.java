package com.lonebytesoft.hamster.eventnotifybot.service.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.service.ResourceUtils;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record UnknownCommand(
        long chatId,
        String command
) implements ExecutableCommand {

    private static final Logger log = LoggerFactory.getLogger(UnknownCommand.class);

    private static final String MESSAGE_TEMPLATE = ResourceUtils.read("message/command/unknown.html");
    private static final String COMMAND_PLACEHOLDER = "%COMMAND%";

    @Override
    public boolean execute(StorageService storageService, TelegramService telegramService) {
        telegramService.sendMessage(
                chatId,
                null,
                MESSAGE_TEMPLATE.replace(COMMAND_PLACEHOLDER, command).trim(),
                false
        );
        log.debug("Sent unknown command message to chat {}, command '{}'", chatId, command);
        return true;
    }

    @Override
    public Integer estimateWriteCost(StorageService storageService) {
        return 0;
    }

}
