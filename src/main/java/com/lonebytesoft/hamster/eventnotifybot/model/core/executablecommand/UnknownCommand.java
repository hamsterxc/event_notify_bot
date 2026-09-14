package com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.service.ResourceUtils;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;

public record UnknownCommand(
        Long chatId,
        String command
) implements ExecutableCommand {

    private static final String MESSAGE_TEMPLATE = ResourceUtils.read("message/unknown_command.html");
    private static final String COMMAND_PLACEHOLDER = "%COMMAND%";

    @Override
    public void execute(StorageService storageService, TelegramService telegramService) {
        telegramService.sendMessage(
                chatId,
                null,
                MESSAGE_TEMPLATE.replace(COMMAND_PLACEHOLDER, command).trim(),
                false
        );
    }

    @Override
    public Integer estimateWriteCost(StorageService storageService) {
        return 0;
    }

}
