package com.dnnt.livebus;

import android.annotation.SuppressLint;
import android.arch.core.internal.SafeIterableMap;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class LiveBus {

    private static Handler mHandler;

    @MainThread
    @SuppressWarnings({"unchecked"})
    public static <T> BusLiveData<T> get(Class<T> eventClass){
        checkMainThread("get");
        String tClassName = eventClass.getName();
        BusLiveData<T> liveData = (BusLiveData<T>) Holder.BUS.get(tClassName);
        if (liveData == null){
            liveData = new BusLiveData<>();
            liveData.tClassName = tClassName;
            Holder.BUS.put(tClassName,(BusLiveData<Object>) liveData);
        }
        return liveData;
    }

    @MainThread
    public static void sendEvent(Object event){
        checkMainThread("sendEvent");
        BusLiveData<Object> liveData = Holder.BUS.get(event.getClass().getName());
        if (liveData != null){
            liveData.setValue(event);
        }
    }

    @MainThread
    public static <T> void setEventSticky(Class<T> eventClass){
        checkMainThread("setEventSticky");
        BusLiveData<T> liveData = get(eventClass);
        liveData.isSticky = true;
    }


    @MainThread
    @SuppressWarnings("unchecked")
    public static void setAndSendSticky(Object event){
        checkMainThread("setAndSendSticky");
        BusLiveData<Object> liveData = get((Class<Object>) event.getClass());
        liveData.isSticky = true;
        liveData.setValue(event);
    }

    public static void postSetAndSendSticky(Object event){
        if (mHandler == null){
            synchronized (LiveBus.class){
                if (mHandler == null){
                    mHandler = new Handler(Looper.getMainLooper());
                }
            }
        }
        Runnable postStickRunnable = new PostStickyRunnable(event);
        mHandler.post(postStickRunnable);
    }

    @MainThread
    public static <T> void removeStickyEvent(Class<T> eventClass){
        checkMainThread("removeStickEvent");
        BusLiveData<Object> liveData = Holder.BUS.get(eventClass.getName());
        if (liveData != null){
            if (liveData.hasObservers()){
                liveData.isSticky = false;
            }else {
                Holder.BUS.remove(eventClass.getName());
            }
        }
    }

    private static void checkMainThread(String method){
        if (Looper.getMainLooper().getThread() != Thread.currentThread()){
            throw new IllegalStateException("You must invoke " + method + " on main thread");
        }
    }

    private static class Holder{
        private static Map<String,BusLiveData<Object>> BUS = new HashMap<>();
    }


    private static class PostStickyRunnable implements Runnable{

        private Object mEvent;

        private PostStickyRunnable(Object event){
            mEvent = event;
        }

        @Override
        public void run() {
            LiveBus.setAndSendSticky(mEvent);
        }
    }


    public static class BusLiveData<T> extends MutableLiveData<T> {

        private final static String LIVE_DATA_VERSION_NAME = "mVersion";

        private String tClassName;

        private boolean isSticky = false;

        @Override
        public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<T> observer) {
            try {
                Field versionField = LiveData.class.getDeclaredField(LIVE_DATA_VERSION_NAME);
                versionField.setAccessible(true);
                final int version = (Integer) versionField.get(this);
                versionField.set(this,Integer.MIN_VALUE);

                super.observe(owner, observer);

                versionField.set(this,version);
                modifyObserverWrapperVersion(version);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e){
                e.printStackTrace();
            }
        }

        @Override
        public void observeForever(@NonNull Observer<T> observer) {
            try {
                Field versionField = LiveData.class.getDeclaredField(LIVE_DATA_VERSION_NAME);
                versionField.setAccessible(true);
                final int version = (Integer) versionField.get(this);
                versionField.set(this,Integer.MIN_VALUE);

                super.observeForever(observer);

                versionField.set(this,version);
                modifyObserverWrapperVersion(version);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        @SuppressLint("PrivateApi")
        @SuppressWarnings({"unchecked"})
        private void modifyObserverWrapperVersion(int liveDataVersion){

            if (liveDataVersion == -1){
                return;
            }

            try {
                Field observersField = LiveData.class.getDeclaredField("mObservers");
                observersField.setAccessible(true);
                SafeIterableMap<Observer<T>,Object> observers = (SafeIterableMap<Observer<T>, Object>) observersField.get(this);
                Object observerWrapper = observers.newest().getValue();

                Class<?> clazz = Class.forName("android.arch.lifecycle.LiveData$ObserverWrapper");
                Field lastVersionField = clazz.getDeclaredField("mLastVersion");
                lastVersionField.setAccessible(true);

                lastVersionField.set(observerWrapper,liveDataVersion);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

        }

        @MainThread
        public void observeSticky(@NonNull LifecycleOwner owner, @NonNull Observer<T> observer) {
            super.observe(owner,observer);
        }

        @MainThread
        public void observeForeverSticky(@NonNull Observer<T> observer){
            super.observeForever(observer);
        }

        @Override
        public void removeObserver(@NonNull Observer<T> observer) {
            super.removeObserver(observer);
            if (!hasObservers() && !isSticky){
                Holder.BUS.remove(tClassName);
            }
        }
    }
}