package com.woxloi.questplugin.utils;

import org.bukkit.boss.BossBar;

import java.util.ArrayList;
import java.util.function.Consumer;

public class STimer {

    public static boolean pluginEnabled = true;

    public int remainingTime = 0;
    public int originalTime = 0;
    boolean timerMoving = false;

    ArrayList<Runnable> onEndEvents = new ArrayList<>();
    ArrayList<Consumer<Integer>> onIntervalEvents = new ArrayList<>();

    public STimer(){
        pluginEnabled = true;
    }

    Thread timerThread = new Thread(() -> {
        //init interval
        for(Consumer<Integer> event: onIntervalEvents){
            event.accept(remainingTime);
        }
        while(timerMoving && pluginEnabled){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if(!timerMoving) continue;

            remainingTime -= 1;
            if(remainingTime <= 0) {
                //timer end
                for(Runnable event: onEndEvents){
                    event.run();
                }
                timerMoving = false;
                return;
            }

            //end of interval events
            for(Consumer<Integer> event: onIntervalEvents){
                event.accept(remainingTime);
            }

        }
    });

    public void setRemainingTime(int time){
        this.remainingTime = time;
        this.originalTime = time;
    }

    public void addRemainingTime(int time){
        remainingTime += time;
    }

    public void removeRemainingTIme(int time){
        remainingTime -= time;
    }

    public void stop(){timerMoving = false;}

    public void start(){
        timerMoving = true;
        if(!timerThread.isAlive()) timerThread.start();
    }

    public void addOnEndEvent(Runnable callback){
        onEndEvents.add(callback);
    }

    public void addOnIntervalEvent(Consumer<Integer> callback){
        onIntervalEvents.add(callback);
    }

    public void linkBossBar(BossBar bar, boolean countDown){
        addOnIntervalEvent(remaining -> {
            double progress = (((double) remaining)-((double) originalTime))/((double) originalTime);
            if(countDown)progress = ((double) remaining)/((double) originalTime);

            bar.setProgress(progress);
        });

        addOnEndEvent(() -> {
            double progress = 100;
            if(countDown) progress = 0;
            bar.setProgress(progress);
        });
    }



}