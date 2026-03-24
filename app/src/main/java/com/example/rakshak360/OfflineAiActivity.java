package com.example.rakshak360;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OfflineAiActivity extends AppCompatActivity {

    private RecyclerView chatRecyclerView;
    private EditText etMessageInput;
    private ImageButton btnSendMessage;
    private ImageButton btnVoiceInput;
    private TextView tvTypingIndicator;
    private TextView tvLiveTypingPreview;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList;

    private static final int VOICE_REQUEST_CODE = 200;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private LlmInference llmInference = null;
    private boolean isModelReady    = false;
    private boolean isModelLoading  = false;

    private static final String MODEL_PATH =
            "/storage/emulated/0/Download/gemma3-1b-it-int4.task";

    private static final String SYSTEM_PROMPT =
            "You are Kavach AI, a friendly and knowledgeable assistant for everyday Indian life. " +
                    "You can answer ANY question the user asks — whether it's about health, cooking, studies, " +
                    "relationships, jobs, finance, technology, safety, travel, general knowledge, or anything else. " +
                    "You think independently and give thoughtful, original answers. " +
                    "Never say you can't answer a general question. " +
                    "Always reply in Hinglish (natural mix of Hindi and English, like Indians actually talk). " +
                    "Be helpful, warm, and concise. Use bullet points when listing things. " +
                    "If the question is about an emergency (police, ambulance, fire), always mention: " +
                    "Police 100, Ambulance 108, Women helpline 1091, All-in-one 112. " +
                    "If the question is about scam/fraud/OTP, warn clearly and advise to call 1930 (Cyber Crime).";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline_ai);

        chatRecyclerView    = findViewById(R.id.chatRecyclerView);
        etMessageInput      = findViewById(R.id.etMessageInput);
        btnSendMessage      = findViewById(R.id.btnSendMessage);
        btnVoiceInput       = findViewById(R.id.btnVoiceInput);
        tvTypingIndicator   = findViewById(R.id.tvTypingIndicator);
        tvLiveTypingPreview = findViewById(R.id.tvLiveTypingPreview);

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(messageList);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(layoutManager);
        chatRecyclerView.setAdapter(chatAdapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        addMessageToChat(
                "Namaste! 🙏 Main Kavach AI hoon.\n\n" +
                        "⏳ AI model load ho raha hai...\n\n" +
                        "Kuch bhi pooch sakte ho — roz ki zindagi ke baare mein:\n" +
                        "• 🍳 Cooking & recipes\n" +
                        "• 💊 Health & symptoms\n" +
                        "• 📚 Studies & career\n" +
                        "• 💰 Finance & savings\n" +
                        "• 🚨 Safety & scam alerts\n" +
                        "• 🔧 Tech help\n" +
                        "• ...aur kuch bhi!",
                false
        );

        checkStoragePermissionAndLoadModel();

        // Live typing preview — WhatsApp style
        etMessageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString().trim();
                if (text.isEmpty()) {
                    tvLiveTypingPreview.setVisibility(View.GONE);
                    tvLiveTypingPreview.setText("");
                    btnVoiceInput.setVisibility(View.VISIBLE);
                    btnSendMessage.setVisibility(View.GONE);
                } else {
                    tvLiveTypingPreview.setVisibility(View.VISIBLE);
                    tvLiveTypingPreview.setText(text);
                    btnVoiceInput.setVisibility(View.GONE);
                    btnSendMessage.setVisibility(View.VISIBLE);
                }
            }
        });

        btnSendMessage.setOnClickListener(v -> sendMessage());
        btnVoiceInput.setOnClickListener(v -> startVoiceInput());
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN,en-IN");
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Bolo... 🎤");
        try {
            startActivityForResult(intent, VOICE_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Voice input support nahi hai is device mein 😔", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendMessage() {
        String userMsg = etMessageInput.getText().toString().trim();
        if (!userMsg.isEmpty()) {
            if (isModelLoading) {
                Toast.makeText(this, "⏳ Model load ho raha hai, thoda wait karo...", Toast.LENGTH_SHORT).show();
                return;
            }
            addMessageToChat(userMsg, true);
            etMessageInput.setText("");
            tvLiveTypingPreview.setVisibility(View.GONE);
            tvTypingIndicator.setVisibility(View.VISIBLE);
            generateAiResponse(userMsg);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VOICE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String spokenText = results.get(0);
                etMessageInput.setText(spokenText);
                etMessageInput.setSelection(spokenText.length());
                sendMessage();
            }
            return;
        }

        if (requestCode == 100) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                Toast.makeText(this, "✅ Permission mili! Loading...", Toast.LENGTH_SHORT).show();
                verifyFileAndLoadModel();
            } else {
                addMessageToChat("❌ Permission nahi mili. App restart karke dobara try karo.", false);
            }
        }
    }

    private void checkStoragePermissionAndLoadModel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                verifyFileAndLoadModel();
            } else {
                showPermissionDialog();
            }
        } else {
            verifyFileAndLoadModel();
        }
    }

    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("📁 Storage Permission Chahiye")
                .setMessage(
                        "Gemma AI model load karne ke liye 'All Files Access' permission chahiye.\n\n" +
                                "Steps:\n1. 'Allow' dabao\n2. 'Rakshak360' dhundho\n" +
                                "3. 'Allow access to all files' ON karo\n4. Wapas app mein aao"
                )
                .setPositiveButton("Allow ✅", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, 100);
                    } catch (Exception e) {
                        startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), 100);
                    }
                })
                .setNegativeButton("Skip", (dialog, which) ->
                        addMessageToChat("⚠️ Model load nahi hua.\nPermission do aur app restart karo. 🙏", false))
                .setCancelable(false)
                .show();
    }

    private void verifyFileAndLoadModel() {
        File modelFile = new File(MODEL_PATH);
        if (!modelFile.exists() || !modelFile.canRead()) {
            addMessageToChat(
                    "❌ Model file nahi mili!\n\n📁 Expected path:\n" + MODEL_PATH +
                            "\n\nFile download karo aur dobara try karo.", false);
            return;
        }
        addMessageToChat("✅ File mili! Gemma AI load ho raha hai... ⏳\n(30–60 sec lag sakte hain)", false);
        initLlmModel();
    }

    private void initLlmModel() {
        isModelLoading = true;
        executorService.execute(() -> tryLoadModel(true));
    }

    private void tryLoadModel(boolean tryGpu) {
        executorService.execute(() -> {
            try {
                LlmInferenceOptions.Builder builder = LlmInferenceOptions.builder()
                        .setModelPath(MODEL_PATH).setMaxTokens(1024);
                try {
                    Class<?> backendClass = Class.forName(
                            "com.google.mediapipe.tasks.genai.llminference.LlmInference$Backend");
                    Object backendValue = java.lang.reflect.Array.get(
                            backendClass.getMethod("values").invoke(null), tryGpu ? 1 : 0);
                    builder.getClass().getMethod("setPreferredBackend", backendClass)
                            .invoke(builder, backendValue);
                } catch (Throwable ignored) {}

                llmInference   = LlmInference.createFromOptions(getApplicationContext(), builder.build());
                isModelReady   = true;
                isModelLoading = false;

                final String mode = tryGpu ? "GPU" : "CPU";
                runOnUiThread(() -> {
                    Toast.makeText(this, "✅ Kavach AI Ready (" + mode + ")!", Toast.LENGTH_LONG).show();
                    addMessageToChat(
                            "✅ AI ready hai! 🎉\n\n100% offline — internet nahi, data leak nahi.\n" +
                                    "Kuch bhi pooch — main khud sochke jawab dunga! 🤖\n\n" +
                                    "💡 Tip: Baat karne ke liye 🎤 mic button bhi use kar sakte ho!", false);
                });

            } catch (OutOfMemoryError oom) {
                isModelLoading = false; isModelReady = false;
                runOnUiThread(() -> addMessageToChat(
                        "❌ RAM kam hai!\n\n1. Baaki apps band karo\n2. Phone restart karo\n3. Dobara open karo", false));

            } catch (Exception e) {
                if (tryGpu) {
                    runOnUiThread(() -> addMessageToChat("⚠️ GPU mode fail. CPU mode try ho raha hai... ⏳", false));
                    tryLoadModel(false);
                } else {
                    isModelLoading = false; isModelReady = false;
                    runOnUiThread(() -> addMessageToChat(
                            "❌ Model load nahi hua.\nError: " + e.getMessage() +
                                    "\n\nPhone restart karo aur dobara try karo.", false));
                }
            }
        });
    }

    private void generateAiResponse(String userMsg) {
        if (!isModelReady || llmInference == null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                tvTypingIndicator.setVisibility(View.GONE);
                addMessageToChat("⚠️ AI model abhi load nahi hua hai.\n\nThoda wait karo ya app restart karo! 🙏", false);
            });
            return;
        }

        String prompt =
                "<start_of_turn>user\n[SYSTEM]: " + SYSTEM_PROMPT +
                        "\n\nUser ka sawaal: " + userMsg +
                        "\n<end_of_turn>\n<start_of_turn>model\n";

        final int[] msgIndex = {-1};
        runOnUiThread(() -> {
            tvTypingIndicator.setVisibility(View.GONE);
            messageList.add(new ChatMessage("⏳ Soch raha hoon...", false));
            msgIndex[0] = messageList.size() - 1;
            chatAdapter.notifyItemInserted(msgIndex[0]);
            chatRecyclerView.scrollToPosition(msgIndex[0]);
        });

        executorService.execute(() -> {
            try {
                String rawResponse = llmInference.generateResponse(prompt);
                String finalText   = cleanResponse(rawResponse);
                if (finalText.isEmpty() || finalText.length() < 5) {
                    finalText = "Hmm, yeh sawaal thoda tricky laga. Thoda aur detail do! 🙂";
                }
                final String display = finalText;
                runOnUiThread(() -> {
                    if (msgIndex[0] >= 0 && msgIndex[0] < messageList.size()) {
                        messageList.get(msgIndex[0]).text = display;
                        chatAdapter.notifyItemChanged(msgIndex[0]);
                        chatRecyclerView.scrollToPosition(msgIndex[0]);
                    }
                });
            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                runOnUiThread(() -> {
                    if (msgIndex[0] >= 0 && msgIndex[0] < messageList.size()) {
                        messageList.get(msgIndex[0]).text = "⚠️ Error: " + err + "\nDobara poochho. 🙏";
                        chatAdapter.notifyItemChanged(msgIndex[0]);
                    }
                });
            }
        });
    }

    private String cleanResponse(String raw) {
        return raw
                .replaceAll("<end_of_turn>[\\s\\S]*", "")
                .replaceAll("<start_of_turn>[\\s\\S]*", "")
                .replaceAll("(?i)^\\s*(model:|assistant:|ai:|kavach ai:|kavach:|\\[system])[:\\s]*", "")
                .replaceAll("\\[SYSTEM][\\s\\S]*?\\n\\n", "")
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                .replaceAll("\\*([^*]+)\\*", "$1")
                .replaceAll("(?m)^\\s*\\*\\s+", "• ")
                .replaceAll("(?m)^\\s*-\\s+", "• ")
                .trim();
    }

    private void addMessageToChat(String text, boolean isUser) {
        runOnUiThread(() -> {
            messageList.add(new ChatMessage(text, isUser));
            int pos = messageList.size() - 1;
            chatAdapter.notifyItemInserted(pos);
            chatRecyclerView.scrollToPosition(pos);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.execute(() -> {
            if (llmInference != null) { try { llmInference.close(); } catch (Exception ignored) {} }
        });
        executorService.shutdown();
    }

    static class ChatMessage {
        String text; boolean isUser;
        ChatMessage(String t, boolean u) { text = t; isUser = u; }
    }

    class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int VIEW_TYPE_AI = 0, VIEW_TYPE_USER = 1;
        List<ChatMessage> messages;
        ChatAdapter(List<ChatMessage> m) { messages = m; }

        @Override public int getItemViewType(int pos) { return messages.get(pos).isUser ? VIEW_TYPE_USER : VIEW_TYPE_AI; }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            return viewType == VIEW_TYPE_USER
                    ? new UserVH(inf.inflate(R.layout.item_chat_user, parent, false))
                    : new AiVH(inf.inflate(R.layout.item_chat_ai, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
            ChatMessage msg = messages.get(pos);
            if (holder instanceof UserVH) ((UserVH) holder).tvChatText.setText(msg.text);
            else ((AiVH) holder).tvMessageAI.setText(msg.text);
        }

        @Override public int getItemCount() { return messages.size(); }

        class UserVH extends RecyclerView.ViewHolder {
            TextView tvChatText;
            UserVH(View v) { super(v); tvChatText = v.findViewById(R.id.tvChatText); }
        }
        class AiVH extends RecyclerView.ViewHolder {
            TextView tvMessageAI;
            AiVH(View v) { super(v); tvMessageAI = v.findViewById(R.id.tvMessageAI); }
        }
    }
}