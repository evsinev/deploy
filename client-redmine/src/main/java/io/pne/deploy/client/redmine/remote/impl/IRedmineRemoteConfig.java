package io.pne.deploy.client.redmine.remote.impl;

import com.payneteasy.startup.parameters.AStartupParameter;
import io.pne.deploy.util.env.IStartupConfig;

public interface IRedmineRemoteConfig extends IStartupConfig {

    @AStartupParameter(name = "REDMINE_URL", value = "")
    String url();

    @AStartupParameter(name = "REDMINE_API_ACCESS_KEY", value = "", maskVariable = true)
    String apiAccessKey();

    @AStartupParameter(name = "REDMINE_STATUS_ACCEPT_ID", value = "1") // new
    int statusAcceptedId();

    @AStartupParameter(name = "REDMINE_STATUS_PROCESSING_ID", value = "2") // in progress
    int statusProcessingId();

    @AStartupParameter(name = "REDMINE_STATUS_DONE_ID", value = "3") // resolved
    int statusDoneId();

    @AStartupParameter(name = "REDMINE_STATUS_FAILED_ID", value = "6") // rejected
    int statusFailedId();

    @AStartupParameter(name = "REDMINE_CONNECT_TIMEOUT", value = "120")
    int connectTimeoutSeconds();

    @AStartupParameter(name = "REDMINE_READ_TIMEOUT", value = "120")
    int readTimeoutSeconds();

    @AStartupParameter(name = "REDMINE_CALLBACK_URI", value = "")
    String redmineCallbackUrl();

    @AStartupParameter(name = "ISSUE_VALIDATION_SCRIPT", value = "")
    String issueValidationScript();

    @AStartupParameter(name = "STATUS_PAGE_PATH", value = "/deploy/status")
    String statusPageHtmlPath();


    @AStartupParameter(name = "TELEGRAM_ENABLED", value = "false")
    boolean isTelegramEnabled();

    @AStartupParameter(name = "TELEGRAM_CHAT_ID", value = "0")
    int getTelegramChatId();

    @AStartupParameter(name = "TELEGRAM_TOKEN", value = "", maskVariable = true)
    String getTelegramToken();

}
