package com.example.rakshak360;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

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

    // RunAnywhere SDK
    private KavachAiManager kavachAiManager;

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
                        "⏳ AI model download & load ho raha hai...\n" +
                        "(Pehli baar ~360MB download hoga, WiFi use karo)\n\n" +
                        "Kuch bhi pooch sakte ho:\n" +
                        "• 🍳 Cooking & recipes\n" +
                        "• 💊 Health & symptoms\n" +
                        "• 📚 Studies & career\n" +
                        "• 💰 Finance & savings\n" +
                        "• 🚨 Safety & scam alerts\n" +
                        "• 🔧 Tech help",
                false
        );

        // KavachAiManager initialize karo
        kavachAiManager = new KavachAiManager(this);
        kavachAiManager.initialize(
                new KavachAiManager.OnProgressCallback() {
                    @Override
                    public void onProgress(int percent) {
                        runOnUiThread(() ->
                                tvTypingIndicator.setText("⬇️ Downloading: " + percent + "%")
                        );
                        tvTypingIndicator.setVisibility(View.VISIBLE);
                    }
                    @Override
                    public void onDone() {
                        runOnUiThread(() ->
                                tvTypingIndicator.setVisibility(View.GONE)
                        );
                    }
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            tvTypingIndicator.setVisibility(View.GONE);
                            addMessageToChat("❌ Download error: " + error, false);
                        });
                    }
                },
                new KavachAiManager.OnReadyCallback() {
                    @Override
                    public void onReady() {
                        runOnUiThread(() -> {
                            Toast.makeText(OfflineAiActivity.this,
                                    "✅ Kavach AI Ready!", Toast.LENGTH_LONG).show();
                            addMessageToChat(
                                    "✅ AI ready hai! 🎉\n\n" +
                                            "100% offline — internet nahi, data leak nahi.\n" +
                                            "Kuch bhi pooch — main khud sochke jawab dunga! 🤖\n\n" +
                                            "💡 Tip: Mic button se bhi baat kar sakte ho!", false);
                        });
                    }
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() ->
                                addMessageToChat("❌ AI load nahi hua: " + error +
                                        "\n\nInternet check karo aur restart karo.", false)
                        );
                    }
                }
        );

        // Live typing preview
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
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Bolo... 🎤");
        try {
            startActivityForResult(intent, VOICE_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this,
                    "Voice input support nahi hai is device mein 😔",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void sendMessage() {
        String userMsg = etMessageInput.getText().toString().trim();
        if (userMsg.isEmpty()) return;

        if (!kavachAiManager.isModelReady()) {
            Toast.makeText(this,
                    "⏳ Model abhi load ho raha hai, thoda wait karo...",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        addMessageToChat(userMsg, true);
        etMessageInput.setText("");
        tvLiveTypingPreview.setVisibility(View.GONE);
        tvTypingIndicator.setVisibility(View.VISIBLE);
        generateAiResponse(userMsg);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String spokenText = results.get(0);
                etMessageInput.setText(spokenText);
                etMessageInput.setSelection(spokenText.length());
                sendMessage();
            }
        }
    }

    private void generateAiResponse(String userMsg) {
        // Placeholder message
        messageList.add(new ChatMessage("⏳ Soch raha hoon...", false));
        final int msgIndex = messageList.size() - 1;
        chatAdapter.notifyItemInserted(msgIndex);
        chatRecyclerView.scrollToPosition(msgIndex);

        kavachAiManager.generateResponse(userMsg, new KavachAiManager.OnResponseCallback() {
            @Override
            public void onToken(String token) {
                // Streaming ke liye — future mein use karenge
            }
            @Override
            public void onComplete(String fullResponse) {
                runOnUiThread(() -> {
                    tvTypingIndicator.setVisibility(View.GONE);
                    String display = fullResponse.trim().isEmpty()
                            ? "Hmm, thoda aur detail do! 🙂"
                            : fullResponse;
                    messageList.get(msgIndex).text = display;
                    chatAdapter.notifyItemChanged(msgIndex);
                    chatRecyclerView.scrollToPosition(msgIndex);
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    tvTypingIndicator.setVisibility(View.GONE);
                    messageList.get(msgIndex).text =
                            "⚠️ Error: " + error + "\nDobara poochho. 🙏";
                    chatAdapter.notifyItemChanged(msgIndex);
                });
            }
        });
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
        if (kavachAiManager != null) kavachAiManager.destroy();
    }

    // ─── Data class ───────────────────────────────────────────
    static class ChatMessage {
        String text; boolean isUser;
        ChatMessage(String t, boolean u) { text = t; isUser = u; }
    }

    // ─── Adapter ──────────────────────────────────────────────
    class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int VIEW_TYPE_AI = 0, VIEW_TYPE_USER = 1;
        List<ChatMessage> messages;
        ChatAdapter(List<ChatMessage> m) { messages = m; }

        @Override
        public int getItemViewType(int pos) {
            return messages.get(pos).isUser ? VIEW_TYPE_USER : VIEW_TYPE_AI;
        }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent, int viewType) {
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