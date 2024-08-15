package com.johnson.automessage.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class AutomessageClient implements ClientModInitializer {

    private static List<AutoMessage> autoMessages = new ArrayList<>();
    private static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        // 初始化5個自動消息
        for (int i = 0; i < 5; i++) {
            autoMessages.add(new AutoMessage("自動發話 開發者Johnson " + (i + 1), 60, false));
        }

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

        // 啟動自動發話計時器
        startAutoChat();
    }

    public static void startAutoChat() {
        for (AutoMessage message : autoMessages) {
            message.start();
        }
    }

    public static void stopAutoChat() {
        for (AutoMessage message : autoMessages) {
            message.stop();
        }
    }

    private static void sendGlobalMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player != null) {
            player.networkHandler.sendChatMessage(message);
        }
    }

    private static class AutoMessage {
        String message;
        int interval;
        boolean isRunning;
        Timer timer;

        AutoMessage(String message, int interval, boolean isRunning) {
            this.message = message;
            this.interval = interval;
            this.isRunning = isRunning;
        }

        void start() {
            if (!isRunning) {
                isRunning = true;
                timer = new Timer();
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        sendGlobalMessage(message);
                    }
                }, 0, interval * 1000);
            }
        }

        void stop() {
            if (isRunning) {
                isRunning = false;
                timer.cancel();
            }
        }

        void updateSettings(String newMessage, int newInterval) {
            this.message = newMessage;
            this.interval = newInterval;
            if (isRunning) {
                stop();
                start();
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
            int spacing = 70; // 增加間距以容納更大的文字框
            int messageWidth = 300; // 增加文字框寬度

            for (int i = 0; i < 5; i++) {
                AutoMessage autoMessage = autoMessages.get(i);
                int currentY = startY + (spacing * i);

                TextFieldWidget messageField = new TextFieldWidget(this.textRenderer, this.width / 2 - messageWidth / 2, currentY, messageWidth, 20, Text.of("文字 " + (i + 1)));
                messageField.setText(autoMessage.message);
                messageField.setMaxLength(256); // 增加最大字符數
                this.addDrawableChild(messageField);
                messageFields.add(messageField);

                TextFieldWidget intervalField = new TextFieldWidget(this.textRenderer, this.width / 2 - messageWidth / 2, currentY + 25, 60, 20, Text.of("數字 (秒)"));
                intervalField.setText(String.valueOf(autoMessage.interval));
                this.addDrawableChild(intervalField);
                intervalFields.add(intervalField);

                ButtonWidget toggleButton = ButtonWidget.builder(Text.of(autoMessage.isRunning ? "停止" : "開始"), button -> {
                    if (autoMessage.isRunning) {
                        autoMessage.stop();
                        button.setMessage(Text.of("開始"));
                    } else {
                        autoMessage.start();
                        button.setMessage(Text.of("停止"));
                    }
                }).dimensions(this.width / 2 + messageWidth / 2 - 60, currentY + 25, 60, 20).build();
                this.addDrawableChild(toggleButton);
                toggleButtons.add(toggleButton);
            }

            this.addDrawableChild(ButtonWidget.builder(Text.of("儲存"), button -> {
                for (int i = 0; i < 5; i++) {
                    AutoMessage autoMessage = autoMessages.get(i);
                    String newMessage = messageFields.get(i).getText();
                    int newInterval;
                    try {
                        newInterval = Integer.parseInt(intervalFields.get(i).getText());
                    } catch (NumberFormatException e) {
                        newInterval = autoMessage.interval;
                    }
                    autoMessage.updateSettings(newMessage, newInterval);
                }
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
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
            super.renderBackground(context, mouseX, mouseY, delta);
        }

        @Override
        public void close() {
            this.client.setScreen(this.parent);
        }
    }
}
