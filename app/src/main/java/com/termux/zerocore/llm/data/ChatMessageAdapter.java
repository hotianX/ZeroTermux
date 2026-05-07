package com.termux.zerocore.llm.data;

import android.content.Context;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.zerocore.llm.markdown.MarkDownAPI;
import com.termux.zerocore.llm.utils.SpannableTextUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.noties.markwon.Markwon;


public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.ChatMessageViewHolder> {
    private List<ChatMessage> messages;
    private Context context;

    public ChatMessageAdapter(Context context, List<ChatMessage> messages) {
        this.context = context;
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isUser() ? R.layout.item_chat_message_user : R.layout.item_chat_message_bot;
    }

    @NonNull
    @Override
    public ChatMessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
        return new ChatMessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatMessageViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        Markwon.Builder builder = Markwon.builder(context);
        MarkDownAPI markDownAPI = MarkDownAPI.create(context);

        Markwon markwon = builder.usePlugin(markDownAPI).build();
        Spanned markdown = markwon.toMarkdown(getDisplayText(message));
        Spanned finalSpanned = SpannableTextUtil.createClickableSpannableString(markdown, context);
        markwon.setParsedMarkdown(holder.messageTextView, finalSpanned);
        holder.messageTextView.setMovementMethod(LinkMovementMethod.getInstance());


        holder.timeTextView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(message.getTimestamp())));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void release() {
        MarkDownAPI.create(context).release();
    }

    public static class ChatMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageTextView;
        TextView timeTextView;

        public ChatMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageTextView = itemView.findViewById(R.id.messageTextView);
            timeTextView = itemView.findViewById(R.id.timeTextView);
        }
    }

    public void updateMessageText(int position, String additionalText) {
        ChatMessage message = messages.get(position);
        message.appendMessageText(additionalText);
        notifyItemChanged(position);
    }

    public void updateReasoningText(int position, String additionalText) {
        ChatMessage message = messages.get(position);
        message.appendReasoningText(additionalText);
        notifyItemChanged(position);
    }

    public void updateToolCallsJson(int position, String toolCallsJson) {
        ChatMessage message = messages.get(position);
        message.setToolCallsJson(toolCallsJson);
        notifyItemChanged(position);
    }

    private String getDisplayText(ChatMessage message) {
        String messageText = message.getMessageText();
        String reasoningText = message.getReasoningText();
        String toolCallsJson = message.getToolCallsJson();
        if (message.isUser()) {
            return messageText;
        }
        String finalText = messageText == null ? "" : messageText;
        if (reasoningText != null && !reasoningText.isEmpty()) {
            finalText = "**Thinking**\n\n> " + reasoningText.replace("\n", "\n> ")
                + "\n\n" + finalText;
        }
        if (toolCallsJson != null && !toolCallsJson.isEmpty()) {
            if (!finalText.isEmpty()) {
                finalText += "\n\n";
            }
            finalText += "**Tool calls**\n\n```json\n" + toolCallsJson + "\n```";
        }
        return finalText;
    }
}
