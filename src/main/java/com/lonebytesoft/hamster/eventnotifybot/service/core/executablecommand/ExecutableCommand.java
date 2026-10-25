package com.lonebytesoft.hamster.eventnotifybot.service.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;

public interface ExecutableCommand {

    boolean execute(StorageService storageService, TelegramService telegramService);

    Integer estimateWriteCost(StorageService storageService);

}
