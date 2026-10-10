package com.example.safezone;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AIAssistantActivity extends AppCompatActivity {

    private LinearLayout chatContainer;
    private ScrollView chatScroll;
    private EditText messageInput;
    private LinearLayout loadingLayout;
    private TextView btnSend;

    private SafetyAssistantService assistantService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_aiassistant);

        chatContainer = findViewById(R.id.chatContainer);
        chatScroll = findViewById(R.id.chatScroll);
        messageInput = findViewById(R.id.messageInput);
        loadingLayout = findViewById(R.id.loadingLayout);
        btnSend = findViewById(R.id.btnSend);

        TextView headerSOS = findViewById(R.id.btnHeaderSOS);
        TextView btnApiKey = findViewById(R.id.btnApiKey);
        TextView quickSOS = findViewById(R.id.quickSOS);
        TextView quickMedical = findViewById(R.id.quickMedical);
        TextView quickFire = findViewById(R.id.quickFire);
        TextView quickPolice = findViewById(R.id.quickPolice);

        assistantService = new SafetyAssistantService(this);

        addBotMessage(
                "Hello! 👋 I am your SAFEZONE AI Safety Assistant.\n\n" +
                        "I am trained to provide guidance on emergency response, fire evacuation, medical first aid, and personal safety.\n\n" +
                        "How can I assist your safety today?",
                true
        );

        btnSend.setOnClickListener(v -> sendMessage());

        btnApiKey.setOnClickListener(v -> showApiKeyDialog());

        headerSOS.setOnClickListener(v -> {
            startActivity(new Intent(AIAssistantActivity.this, SOSActivity.class));
        });

        quickSOS.setOnClickListener(v ->
                askQuestion("What should I do immediately during an active emergency?")
        );

        quickMedical.setOnClickListener(v ->
                askQuestion("What are the vital steps for a medical emergency?")
        );

        quickFire.setOnClickListener(v ->
                askQuestion("What should I do if a building catches fire?")
        );

        quickPolice.setOnClickListener(v ->
                askQuestion("What should I do if I feel threatened, stalked, or robbed?")
        );
    }

    private void showApiKeyDialog() {
        final EditText input = new EditText(this);
        input.setHint("Paste Gemini API Key (AIzaSy...)");
        String currentKey = assistantService.getActiveApiKey();
        if (currentKey != null && !currentKey.isEmpty()) {
            input.setText(currentKey);
        }
        input.setSingleLine(false);
        input.setMaxLines(3);
        input.setPadding(40, 30, 40, 30);

        new AlertDialog.Builder(this)
                .setTitle("Gemini API Key")
                .setMessage("Paste your Google AI Studio Gemini API key to activate generative AI safety responses.")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newKey = input.getText().toString().trim();
                    assistantService.saveApiKey(newKey);
                    Toast.makeText(this, newKey.isEmpty() ? "API key removed" : "Gemini API key saved & connected!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setLoading(boolean isLoading) {
        loadingLayout.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnSend.setEnabled(!isLoading);
        btnSend.setAlpha(isLoading ? 0.5f : 1.0f);
        if (isLoading) {
            scrollToBottom();
        }
    }

    private void sendMessage() {
        String message = messageInput.getText().toString().trim();
        if (message.isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show();
            return;
        }

        addUserMessage(message);
        messageInput.setText("");
        setLoading(true);

        assistantService.generateResponse(message, new SafetyAssistantService.AssistantCallback() {
            @Override
            public void onResponse(String reply, boolean isGenerativeAi) {
                setLoading(false);
                addBotMessage(reply, isGenerativeAi);
            }

            @Override
            public void onError(String errorMessage) {
                setLoading(false);
                Toast.makeText(AIAssistantActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void askQuestion(String question) {
        addUserMessage(question);
        setLoading(true);

        assistantService.generateResponse(question, new SafetyAssistantService.AssistantCallback() {
            @Override
            public void onResponse(String reply, boolean isGenerativeAi) {
                setLoading(false);
                addBotMessage(reply, isGenerativeAi);
            }

            @Override
            public void onError(String errorMessage) {
                setLoading(false);
                Toast.makeText(AIAssistantActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addUserMessage(String message) {
        String timestamp = getCurrentTime();

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(36, 24, 36, 24);
        bubble.setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_bubble_user));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.END;
        params.setMargins(80, 10, 8, 10);
        bubble.setLayoutParams(params);

        TextView senderText = new TextView(this);
        senderText.setText("You • " + timestamp);
        senderText.setTextSize(12);
        senderText.setTextColor(Color.parseColor("#BFDBFE"));
        senderText.setTypeface(null, Typeface.BOLD);
        senderText.setPadding(0, 0, 0, 4);

        TextView bodyText = new TextView(this);
        bodyText.setText(message);
        bodyText.setTextSize(15);
        bodyText.setTextColor(Color.WHITE);
        bodyText.setLineSpacing(4, 1.15f);

        bubble.addView(senderText);
        bubble.addView(bodyText);

        bubble.setAlpha(0f);
        bubble.setTranslationY(15f);
        chatContainer.addView(bubble);
        bubble.animate().alpha(1f).translationY(0f).setDuration(180).start();
        scrollToBottom();
    }

    private void addBotMessage(String message, boolean isGenerativeAi) {
        String timestamp = getCurrentTime();
        String sourceLabel = isGenerativeAi ? "🤖 SAFEZONE AI (Gemini)" : "🛡️ SAFEZONE Safety Engine";

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(36, 26, 36, 26);
        bubble.setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_bubble_ai));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.START;
        params.setMargins(8, 10, 80, 10);
        bubble.setLayoutParams(params);

        TextView senderText = new TextView(this);
        senderText.setText(sourceLabel + " • " + timestamp);
        senderText.setTextSize(12);
        senderText.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.brand_accent));
        senderText.setTypeface(null, Typeface.BOLD);
        senderText.setPadding(0, 0, 0, 6);

        TextView bodyText = new TextView(this);
        bodyText.setText(message);
        bodyText.setTextSize(15);
        bodyText.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.brand_primary));
        bodyText.setLineSpacing(4, 1.15f);

        bubble.addView(senderText);
        bubble.addView(bodyText);

        bubble.setAlpha(0f);
        bubble.setTranslationY(15f);
        chatContainer.addView(bubble);
        bubble.animate().alpha(1f).translationY(0f).setDuration(180).start();
        scrollToBottom();
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
    }

    private void scrollToBottom() {
        chatScroll.post(() -> chatScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }
}