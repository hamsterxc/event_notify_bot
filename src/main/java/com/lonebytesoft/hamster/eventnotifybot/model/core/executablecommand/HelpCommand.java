package com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.service.ResourceUtils;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;

import java.util.Optional;

public record HelpCommand(
        Long chatId
) implements ExecutableCommand {

    private static final String REPOSITORY_PLACEHOLDER = "%REPOSITORY%";
    private static final String COMMIT_ID_PLACEHOLDER = "%COMMIT_ID%";
    private static final String CLEAN_BUILD_NOTICE_PLACEHOLDER = "%CLEAN_BUILD_NOTICE%";
    private static final String MESSAGE = ResourceUtils.read("message/help_command.html")
            .replace(REPOSITORY_PLACEHOLDER, "https://github.com/hamsterxc/event_notify_bot")
            .replace(COMMIT_ID_PLACEHOLDER, Optional.ofNullable(System.getenv("COMMIT_ID")).orElse(""))
            .replace(CLEAN_BUILD_NOTICE_PLACEHOLDER, "true".equalsIgnoreCase(System.getenv("IS_CLEAN_BUILD")) ? "" : "based on ")
            .trim();

    @Override
    public boolean execute(StorageService storageService, TelegramService telegramService) {
        telegramService.sendMessage(
                chatId,
                null,
                MESSAGE,
                false
        );
        return true;
    }

    @Override
    public Integer estimateWriteCost(StorageService storageService) {
        return 0;
    }

}
