/*
 *  Copyright (C) 2019 SmartLife Robotics, Poland
 *  See COPYING for the license
 */
package com.swps.pepperxapp.robot;

import android.util.Log;
import android.util.TypedValue;

import com.aldebaran.qi.Consumer;
import com.aldebaran.qi.Future;
import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks;
import com.aldebaran.qi.sdk.builder.AnimateBuilder;
import com.aldebaran.qi.sdk.builder.AnimationBuilder;
import com.aldebaran.qi.sdk.builder.ChatBuilder;
import com.aldebaran.qi.sdk.builder.QiChatbotBuilder;
import com.aldebaran.qi.sdk.builder.SayBuilder;
import com.aldebaran.qi.sdk.builder.TopicBuilder;
import com.aldebaran.qi.sdk.object.actuation.Animate;
import com.aldebaran.qi.sdk.object.actuation.Animation;
import com.aldebaran.qi.sdk.object.conversation.BodyLanguageOption;
import com.aldebaran.qi.sdk.object.conversation.Chat;
import com.aldebaran.qi.sdk.object.conversation.Chatbot;
import com.aldebaran.qi.sdk.object.conversation.Phrase;
import com.aldebaran.qi.sdk.object.conversation.QiChatExecutor;
import com.aldebaran.qi.sdk.object.conversation.QiChatbot;
import com.aldebaran.qi.sdk.object.conversation.Say;
import com.aldebaran.qi.sdk.object.conversation.Topic;
import com.aldebaran.qi.sdk.object.locale.Language;
import com.aldebaran.qi.sdk.object.locale.Locale;
import com.aldebaran.qi.sdk.object.locale.Region;
import com.swps.pepperxapp.R;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class that gathers main robot-related operations of our application.
 */
public class Robot implements RobotLifecycleCallbacks {

    private static final String TAG = "Robot";

    public static final int MAX_RECOMMENDATION = 10;

    private Chat chat;
    private Future<Void> fchat ;
    private QiContext qiContext;
    private UiNotifier uiNotifier;
    private QiChatbot qiChatbot;

    public Robot(UiNotifier uiNotifier) {
        this.uiNotifier = uiNotifier;
    }

    @Override
    public void onRobotFocusGained(final QiContext theContext) {
        Log.d(TAG, "onRobotFocusGained");
        this.qiContext = theContext;


        // Create a phrase.
        Phrase phrase = new Phrase("Gdzie jest Konrad?");

        // Build the action.
        Locale locale = new Locale(Language.POLISH, Region.POLAND);
        Say say = SayBuilder.with(qiContext)
                .withPhrase(phrase)
                .withLocale(locale)
                .build();

        // Run the action synchronously.
        say.run();

        // Now that the focus is owned by this app, the chat can be run
        runChat();
    }

    @Override
    public void onRobotFocusLost() {
        Log.d(TAG, "onRobotFocusLost");
        this.qiContext = null;

        removeChatListeners();
    }

    @Override
    public void onRobotFocusRefused(final String reason) {
        Log.e(TAG, "Robot is not available: " + reason);
    }

    private void runChat() {
        Log.d(TAG, "runChat()");

        // Create chatbots
        qiChatbot = createQiChatbot();
        uiNotifier.updateQiChatSuggestions(qiChatbot.globalRecommendations(100));
        //Chatbot dialogFlowChatbot = new DialogflowChatbot(qiContext, uiNotifier);
        setExecutor();
        // Create the chat from its chatbots
        chat = ChatBuilder.with(qiContext)
                .withLocale(new Locale(Language.ENGLISH, Region.UNITED_STATES))
                .withChatbot(qiChatbot/*, dialogFlowChatbot*/)
                .build();

        setChatListeners();
        fchat  = chat.async().run();
    }

    private void stopChat(){
        fchat.requestCancellation();
        //fchat = null;
        //chat = null;
        //qiChatbot = null;
    }

    private void setExecutor() {
        Map<String, QiChatExecutor> executors = new HashMap<>();

        // Map the executor name from the topic to our qiChatbotExecutor
        executors.put("launchAnimation", new MyQiChatExecutor(qiContext));
        executors.put("showImage", new UiImageExecutor(qiContext, this.uiNotifier));
        executors.put("playSound", new UiPlaySoundExecutor(qiContext, this.uiNotifier));
        // Set the executors to the qiChatbot
        qiChatbot.setExecutors(executors);
    }

    private QiChatbot createQiChatbot() {

        // Create the QiChatbot from a topic
        return QiChatbotBuilder.with(qiContext)
                .withTopics(getTopics())
                .build();
    }

    private void setChatListeners() {
        chat.addOnStartedListener(new Chat.OnStartedListener() {
            @Override
            public void onStarted() {
                Log.i(TAG, "chat.onStarted()");
            }
        });

        chat.addOnListeningChangedListener(new Chat.OnListeningChangedListener() {
            @Override
            public void onListeningChanged(final Boolean aBoolean) {
                Log.i(TAG, "chat.onListeningChanged(): " + aBoolean);
            }
        });

        chat.addOnSayingChangedListener(new Chat.OnSayingChangedListener() {
            @Override
            public void onSayingChanged(final Phrase phrase) {
                uiNotifier.setText(phrase.getText());
                Log.i(TAG, "chat.onSayingChanged(): " + phrase.getText());
            }
        });

        chat.addOnHeardListener(new Chat.OnHeardListener() {
            @Override
            public void onHeard(final Phrase phrase) {
                Log.i(TAG, "chat.onHeard: " + phrase.getText());
            }
        });

        chat.addOnNoPhraseRecognizedListener(new Chat.OnNoPhraseRecognizedListener() {
            @Override
            public void onNoPhraseRecognized() {
                Log.i(TAG, "chat.onNoPhraseRecognized()");
            }
        });

        chat.addOnNormalReplyFoundForListener(new Chat.OnNormalReplyFoundForListener() {
            @Override
            public void onNormalReplyFoundFor(final Phrase input) {
                Log.i(TAG, "chat.onNormalReplyFoundFor() phrase.getText() = " + input.getText());
            }
        });

        chat.addOnFallbackReplyFoundForListener(new Chat.OnFallbackReplyFoundForListener() {
            @Override
            public void onFallbackReplyFoundFor(final Phrase input) {
                Log.i(TAG, "chat.onFallbackReplyFoundFor() input.getText() =" + input.getText());
            }
        });

        chat.addOnNoReplyFoundForListener(new Chat.OnNoReplyFoundForListener() {
            @Override
            public void onNoReplyFoundFor(final Phrase input) {
                Log.i(TAG, "chat.onNoReplyFoundFor() input.getText() = " + input.getText());
            }
        });
    }

    private void removeChatListeners() {
        if (chat == null) {
            return;
        }

        chat.removeAllOnStartedListeners();
        chat.removeAllOnListeningChangedListeners();
        chat.removeAllOnSayingChangedListeners();
        chat.removeAllOnHeardListeners();
        chat.removeAllOnNoPhraseRecognizedListeners();
        chat.removeAllOnNormalReplyFoundForListeners();
        chat.removeAllOnFallbackReplyFoundForListeners();
        chat.removeAllOnNoReplyFoundForListeners();
    }


    private List<Topic> getTopics() {
        List<Topic> topics = new ArrayList<>();
        for (Field r : R.raw.class.getFields()) {
            try {
                TypedValue value = new TypedValue();
                qiContext.getResources().getValue(r.getInt(r), value, true);
                if (value.string.toString().endsWith(".top")) {
                    topics.add(TopicBuilder.with(qiContext)
                            .withResource(r.getInt(r))
                            .build());
                }

            } catch (IllegalAccessException e) {
                Log.i(TAG, e.getMessage());
            }
        }

        return topics;
    }

    public void performAction() {

        stopChat();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(5000);

                    // Create an animation.
                    Future<Animation> animation = AnimationBuilder.with(qiContext) // Create the builder with the context.
                            .withResources(R.raw.both_hands_high_b001) // Set the animation resource.
                            .buildAsync(); // Build the animation.

                    // Create an animate action.
                    Future<Animate> animate = AnimateBuilder.with(qiContext) // Create the builder with the context.
                            .withAnimation(animation.getValue()) // Set the animation.
                            .buildAsync(); // Build the animate action.

                    // Run the animate action asynchronously.
                    Future<Void> animateFuture = animate.getValue().async().run();

                    Locale locale = new Locale(Language.POLISH, Region.POLAND);
                    Future<Say> say = SayBuilder.with(qiContext)
                            .withPhrase(new Phrase("Nie mogę się doczekać naszej konferencji dwudziestego dziewiątego listopada!"))
                            .withLocale(locale)
                            .buildAsync(); // OK.

                    say.getValue().async().run();
                }
                catch (Exception e)
                {
                    Log.e("Robot", e.getMessage());
                }

            }
        }).run();



    }

}
