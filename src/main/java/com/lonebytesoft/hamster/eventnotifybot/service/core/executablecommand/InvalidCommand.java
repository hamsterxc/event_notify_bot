package com.lonebytesoft.hamster.eventnotifybot.service.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.service.ResourceUtils;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record InvalidCommand(
        long chatId,
        String error
) implements ExecutableCommand {

    private static final Logger log = LoggerFactory.getLogger(InvalidCommand.class);

    private static final String MESSAGE_TEMPLATE = ResourceUtils.read("message/command/invalid.html");
    private static final String ERROR_PLACEHOLDER = "%ERROR%";

    @Override
    public boolean execute(StorageService storageService, TelegramService telegramService) {
        telegramService.sendMessage(
                chatId,
                null,
                MESSAGE_TEMPLATE.replace(ERROR_PLACEHOLDER, error).trim(),
                false
        );
        log.debug("Sent error message to chat {}: {}", chatId, error);
        return true;
    }

    @Override
    public Integer estimateWriteCost(StorageService storageService) {
        return 0;
    }

}
