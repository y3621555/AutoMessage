package com.johnson.automessage.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
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
import java.util.List;

public class AutomessageClient implements ClientModInitializer {

    private static List<AutoMessage> autoMessages = new ArrayList<>();
    private static KeyBinding openGuiKey;
    private static final String CONFIG_FILE = "automessage_config.json";
    private static Thread messageThread;
    private static volatile boolean shouldRun = true;

    @Override
    public void onInitializeClient() {
        loadConfig();

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "自動發話",
                GLFW.GLFW_KEY_K,
                "自動發話模組"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                client.setScreen(new AutoChatScreen(client.currentScreen));
            }
        });

        startMessageThread();
    }

    private static void loadConfig() {
        File configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), CONFIG_FILE);
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

    private static void saveConfig() {
        File configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), CONFIG_FILE);
        try (FileWriter writer = new FileWriter(configFile)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(autoMessages, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void startMessageThread() {
        messageThread = new Thread(() -> {
            while (shouldRun) {
                for (AutoMessage message : autoMessages) {
                    if (message.isRunning) {
                        message.tryToSend();
                    }
                }
                try {
                    Thread.sleep(1000); // 每秒檢查一次
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        messageThread.start();
    }

    private static void sendGlobalMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.execute(() -> {
                try {
                    client.player.networkHandler.sendChatMessage(message);
                } catch (Exception e) {
                    System.out.println("無法發送消息: " + e.getMessage());
                }
            });
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

        void tryToSend() {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSentTime >= interval * 1000) {
                sendGlobalMessage(message);
                lastSentTime = currentTime;
            }
        }
    }

    public static class AutoChatScreen extends Screen {
        private final Screen parent;
        private List<TextFieldWidget> messageFields = new ArrayList<>();
        private List<TextFieldWidget> intervalFields = new ArrayList<>();
        private List<ButtonWidget> toggleButtons = new ArrayList<>();

        public AutoChatScreen(Screen parent) {
            super(Text.of("自動發話設定"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int startY = 30;
            int spacing = 70;
            int messageWidth = 300;

            for (int i = 0; i < 5; i++) {
                AutoMessage autoMessage = autoMessages.get(i);
                int currentY = startY + (spacing * i);

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
                    // 不需要更新 isRunning，因為它已經在按鈕點擊時更新了
                }
                saveConfig();
                this.close();
            }).dimensions(this.width / 2 - 100, this.height - 40, 200, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context, mouseX, mouseY, delta);
            context.drawTextWithShadow(this.textRenderer, this.title, (this.width - this.textRenderer.getWidth(this.title)) / 2, 10, 0xFFFFFF);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public void close() {
            this.client.setScreen(this.parent);
        }
    }
}
