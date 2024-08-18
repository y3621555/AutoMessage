package com.johnson.automessage.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import net.fabricmc.loader.api.FabricLoader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;;



public class AutomessageClient implements ClientModInitializer {

    private static List<AutoMessage> autoMessages = new ArrayList<>();
    private static List<AutoCommand> autoCommands = new ArrayList<>();
    private static KeyBinding openMessagesGuiKey;
    private static KeyBinding openCommandsGuiKey;
    private static final String MESSAGES_CONFIG_FILE = "automessage_config.json";
    private static final String COMMANDS_CONFIG_FILE = "autocommand_config.json";
    private static volatile boolean shouldRun = true;

    @Override
    public void onInitializeClient() {
        loadMessagesConfig();
        loadCommandsConfig();

        openMessagesGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "自動發話", GLFW.GLFW_KEY_K, "自動發話模組"
        ));

        openCommandsGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "自動指令", GLFW.GLFW_KEY_L, "自動發話模組"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMessagesGuiKey.wasPressed()) {
                client.setScreen(new AutoChatScreen(client.currentScreen));
            }
            while (openCommandsGuiKey.wasPressed()) {
                client.setScreen(new AutoCommandScreen(client.currentScreen));
            }

            // 每次主線程tick時檢查消息和命令
            if (shouldRun) {
                for (AutoMessage message : autoMessages) {
                    if (message.isRunning) {
                        message.tryToSend(client);
                    }
                }
                for (AutoCommand command : autoCommands) {
                    if (command.isRunning) {
                        command.tryToExecute(client);
                    }
                }
            }
        });
    }

    private static void loadMessagesConfig() {
        File configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), MESSAGES_CONFIG_FILE);
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                Type listType = new TypeToken<ArrayList<AutoMessage>>(){}.getType();
                autoMessages = new Gson().fromJson(reader, listType);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            for (int i = 0; i < 5; i++) {
                autoMessages.add(new AutoMessage("自動發話 開發者Johnson " + (i + 1), 60, false));
            }
        }
    }

    private static void saveMessagesConfig() {
        File configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), MESSAGES_CONFIG_FILE);
        try (FileWriter writer = new FileWriter(configFile)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(autoMessages, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadCommandsConfig() {
        File configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), COMMANDS_CONFIG_FILE);
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                Type listType = new TypeToken<ArrayList<AutoCommand>>(){}.getType();
                autoCommands = new Gson().fromJson(reader, listType);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            for (int i = 0; i < 3; i++) {
                autoCommands.add(new AutoCommand("/say 自動指令 " + (i + 1), 60, false));
            }
        }
    }

    private static void saveCommandsConfig() {
        File configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), COMMANDS_CONFIG_FILE);
        try (FileWriter writer = new FileWriter(configFile)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(autoCommands, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    private static void sendGlobalMessage(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.networkHandler.sendChatMessage(message);
        }
    }

    private static void executeCommand(MinecraftClient client, String command) {
        if (client.player != null) {
            client.player.networkHandler.sendCommand(command.substring(1)); // 移除開頭的 "/"
        }
    }

    private static class AutoMessage {
        String message;
        int interval;
        boolean isRunning;
        long lastSentTime;

        AutoMessage(String message, int interval, boolean isRunning) {
            this.message = message;
            this.interval = interval;
            this.isRunning = isRunning;
            this.lastSentTime = 0;
        }

        void tryToSend(MinecraftClient client) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSentTime >= interval * 1000) {
                sendGlobalMessage(client, message);
                lastSentTime = currentTime;
            }
        }
    }

    private static class AutoCommand {
        String command;
        int interval;
        boolean isRunning;
        long lastExecutedTime;

        AutoCommand(String command, int interval, boolean isRunning) {
            this.command = command;
            this.interval = interval;
            this.isRunning = isRunning;
            this.lastExecutedTime = 0;
        }

        void tryToExecute(MinecraftClient client) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastExecutedTime >= interval * 1000) {
                executeCommand(client, command);
                lastExecutedTime = currentTime;
            }
        }
    }

    public static class AutoChatScreen extends Screen {
        private final Screen parent;
        private List<TextFieldWidget> messageFields = new ArrayList<>();
        private List<TextFieldWidget> intervalFields = new ArrayList<>();
        private List<ButtonWidget> toggleButtons = new ArrayList<>();
        private int scrollOffset = 0;
        private static final int ITEM_HEIGHT = 65;
        private int visibleItems;
        private int contentHeight;

        public AutoChatScreen(Screen parent) {
            super(Text.of("自動發話設定"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            visibleItems = (this.height - 80) / ITEM_HEIGHT;
            contentHeight = 5 * ITEM_HEIGHT; // 5個項目的總高度
            int startY = 30;
            int messageWidth = Math.min(300, this.width - 40);

            for (int i = 0; i < 5; i++) {
                AutoMessage autoMessage = autoMessages.get(i);
                int currentY = startY + (ITEM_HEIGHT * i);

                TextFieldWidget messageField = new TextFieldWidget(this.textRenderer, this.width / 2 - messageWidth / 2, currentY, messageWidth, 20, Text.of("文字 " + (i + 1)));
                messageField.setText(autoMessage.message);
                messageField.setMaxLength(256);
                this.addDrawableChild(messageField);
                messageFields.add(messageField);

                TextFieldWidget intervalField = new TextFieldWidget(this.textRenderer, this.width / 2 - messageWidth / 2, currentY + 25, 60, 20, Text.of("數字 (秒)"));
                intervalField.setText(String.valueOf(autoMessage.interval));
                this.addDrawableChild(intervalField);
                intervalFields.add(intervalField);

                ButtonWidget toggleButton = ButtonWidget.builder(Text.of(autoMessage.isRunning ? "啟用" : "禁用"), button -> {
                    autoMessage.isRunning = !autoMessage.isRunning;
                    button.setMessage(Text.of(autoMessage.isRunning ? "啟用" : "禁用"));
                }).dimensions(this.width / 2 + messageWidth / 2 - 60, currentY + 25, 60, 20).build();
                this.addDrawableChild(toggleButton);
                toggleButtons.add(toggleButton);
            }

            this.addDrawableChild(ButtonWidget.builder(Text.of("儲存"), button -> {
                for (int i = 0; i < 5; i++) {
                    AutoMessage autoMessage = autoMessages.get(i);
                    autoMessage.message = messageFields.get(i).getText();
                    try {
                        autoMessage.interval = Integer.parseInt(intervalFields.get(i).getText());
                    } catch (NumberFormatException e) {
                        // 如果輸入無效，保持原來的間隔
                    }
                }
                saveMessagesConfig();
                this.close();
            }).dimensions(this.width / 2 - 100, this.height - 30, 200, 20).build());
        }


        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context, mouseX, mouseY, delta);
            context.drawTextWithShadow(this.textRenderer, this.title, (this.width - this.textRenderer.getWidth(this.title)) / 2, 10, 0xFFFFFF);

            int messageWidth = Math.min(300, this.width - 40);

            // 創建一個剪裁區域，只在這個區域內繪製元素
            context.enableScissor(0, 30, this.width, this.height - 40);

            // 繪製可見的元素
            for (int i = 0; i < 5; i++) {
                int currentY = 30 + (ITEM_HEIGHT * i) - scrollOffset;
                if (currentY > -ITEM_HEIGHT && currentY < this.height) {
                    messageFields.get(i).setX(this.width / 2 - messageWidth / 2);
                    messageFields.get(i).setY(currentY);
                    messageFields.get(i).setWidth(messageWidth);
                    messageFields.get(i).render(context, mouseX, mouseY, delta);

                    intervalFields.get(i).setX(this.width / 2 - messageWidth / 2);
                    intervalFields.get(i).setY(currentY + 25);
                    intervalFields.get(i).render(context, mouseX, mouseY, delta);

                    toggleButtons.get(i).setX(this.width / 2 + messageWidth / 2 - 60);
                    toggleButtons.get(i).setY(currentY + 25);
                    toggleButtons.get(i).render(context, mouseX, mouseY, delta);
                }
            }

            context.disableScissor();

            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int)(verticalAmount * 15), Math.max(0, contentHeight - (this.height - 80))));
            return true;
        }

        @Override
        public void resize(MinecraftClient client, int width, int height) {
            super.resize(client, width, height);
            this.init(client, width, height);
        }

        @Override
        public void close() {
            this.client.setScreen(this.parent);
        }
    }

    public static class AutoCommandScreen extends Screen {
        private final Screen parent;
        private List<TextFieldWidget> commandFields = new ArrayList<>();
        private List<TextFieldWidget> intervalFields = new ArrayList<>();
        private List<ButtonWidget> toggleButtons = new ArrayList<>();
        private int scrollOffset = 0;
        private static final int ITEM_HEIGHT = 65;
        private int visibleItems;
        private int contentHeight;

        public AutoCommandScreen(Screen parent) {
            super(Text.of("自動指令設定"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            visibleItems = (this.height - 80) / ITEM_HEIGHT;
            contentHeight = 3 * ITEM_HEIGHT; // 3個項目的總高度
            int startY = 30;
            int commandWidth = Math.min(300, this.width - 40);

            for (int i = 0; i < 3; i++) {
                AutoCommand autoCommand = autoCommands.get(i);
                int currentY = startY + (ITEM_HEIGHT * i);

                TextFieldWidget commandField = new TextFieldWidget(this.textRenderer, this.width / 2 - commandWidth / 2, currentY, commandWidth, 20, Text.of("指令 " + (i + 1)));
                commandField.setText(autoCommand.command);
                commandField.setMaxLength(256);
                this.addDrawableChild(commandField);
                commandFields.add(commandField);

                TextFieldWidget intervalField = new TextFieldWidget(this.textRenderer, this.width / 2 - commandWidth / 2, currentY + 25, 60, 20, Text.of("數字 (秒)"));
                intervalField.setText(String.valueOf(autoCommand.interval));
                this.addDrawableChild(intervalField);
                intervalFields.add(intervalField);

                ButtonWidget toggleButton = ButtonWidget.builder(Text.of(autoCommand.isRunning ? "啟用" : "禁用"), button -> {
                    autoCommand.isRunning = !autoCommand.isRunning;
                    button.setMessage(Text.of(autoCommand.isRunning ? "啟用" : "禁用"));
                }).dimensions(this.width / 2 + commandWidth / 2 - 60, currentY + 25, 60, 20).build();
                this.addDrawableChild(toggleButton);
                toggleButtons.add(toggleButton);
            }

            this.addDrawableChild(ButtonWidget.builder(Text.of("儲存"), button -> {
                for (int i = 0; i < 3; i++) {
                    AutoCommand autoCommand = autoCommands.get(i);
                    autoCommand.command = commandFields.get(i).getText();
                    try {
                        autoCommand.interval = Integer.parseInt(intervalFields.get(i).getText());
                    } catch (NumberFormatException e) {
                        // 如果輸入無效，保持原來的間隔
                    }
                }
                saveCommandsConfig();
                this.close();
            }).dimensions(this.width / 2 - 100, this.height - 30, 200, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context, mouseX, mouseY, delta);
            context.drawTextWithShadow(this.textRenderer, this.title, (this.width - this.textRenderer.getWidth(this.title)) / 2, 10, 0xFFFFFF);

            int commandWidth = Math.min(300, this.width - 40);

            context.enableScissor(0, 30, this.width, this.height - 40);

            for (int i = 0; i < 3; i++) {
                int currentY = 30 + (ITEM_HEIGHT * i) - scrollOffset;
                if (currentY > -ITEM_HEIGHT && currentY < this.height) {
                    commandFields.get(i).setX(this.width / 2 - commandWidth / 2);
                    commandFields.get(i).setY(currentY);
                    commandFields.get(i).setWidth(commandWidth);
                    commandFields.get(i).render(context, mouseX, mouseY, delta);

                    intervalFields.get(i).setX(this.width / 2 - commandWidth / 2);
                    intervalFields.get(i).setY(currentY + 25);
                    intervalFields.get(i).render(context, mouseX, mouseY, delta);

                    toggleButtons.get(i).setX(this.width / 2 + commandWidth / 2 - 60);
                    toggleButtons.get(i).setY(currentY + 25);
                    toggleButtons.get(i).render(context, mouseX, mouseY, delta);
                }
            }

            context.disableScissor();

            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int)(verticalAmount * 15), Math.max(0, contentHeight - (this.height - 80))));
            return true;
        }

        @Override
        public void resize(MinecraftClient client, int width, int height) {
            super.resize(client, width, height);
            this.init(client, width, height);
        }

        @Override
        public void close() {
            this.client.setScreen(this.parent);
        }
    }
}
