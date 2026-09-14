package com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.service.ResourceUtils;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;

public record InvalidCommand(
        Long chatId,
        String error
) implements ExecutableCommand {

    private static final String MESSAGE_TEMPLATE = ResourceUtils.read("message/invalid_command.html");
    private static final String ERROR_PLACEHOLDER = "%ERROR%";

    @Override
    public void execute(StorageService storageService, TelegramService telegramService) {
        telegramService.sendMessage(
                chatId,
                null,
                MESSAGE_TEMPLATE.replace(ERROR_PLACEHOLDER, error).trim(),
                false
        );
    }

    @Override
    public Integer estimateWriteCost(StorageService storageService) {
        return 0;
    }

}
