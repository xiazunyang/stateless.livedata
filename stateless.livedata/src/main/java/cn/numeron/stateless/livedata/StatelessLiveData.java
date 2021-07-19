package cn.numeron.stateless.livedata;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.arch.core.util.Function;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.numeron.android.AppRuntime;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 新添加的观察者，不会收到之前的数据回调，只会在之后收到回调或直到被移除
 */
public class StatelessLiveData<T> extends LiveData<T> {

    private T value = null;
    private int activeCount = 0;
    private T pendingValue = null;
    private final Set<ObserverWrapper> observers = new LinkedHashSet<>();
    private final Runnable postRunnable = new Runnable() {
        @Override
        public synchronized void run() {
            if (pendingValue != null) {
                //使用newValue的作用，是为了在setValue之前，能将pendingValue设置为null
                T newValue = pendingValue;
                pendingValue = null;
                setValue(newValue);
            }
        }
    };

    public StatelessLiveData(@NonNull T value) {
        this.value = value;
    }

    public StatelessLiveData() {
    }

    @Nullable
    @Override
    public T getValue() {
        return value;
    }

    @Override
    public void setValue(final T value) {
        AppRuntime.INSTANCE.getMainExecutor().assertMainThread("setValue");
        this.value = value;
        notifyObservers(value);
    }

    @Override
    public void postValue(final T value) {
        this.pendingValue = value;
        AppRuntime.INSTANCE.getMainExecutor().execute(postRunnable);
    }

    @Override
    public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
        AppRuntime.INSTANCE.getMainExecutor().assertMainThread("observe");
        LifecycleObserver lifecycleObserver = new LifecycleObserver(owner, observer);
        observers.add(lifecycleObserver);
    }

    @Override
    public void observeForever(@NonNull Observer<? super T> observer) {
        AppRuntime.INSTANCE.getMainExecutor().assertMainThread("observeForever");
        ObserverWrapper observerWrapper = new ObserverWrapper(observer);
        observers.add(observerWrapper);
        observerWrapper.activeStateChanged(true);
    }

    @Override
    public void removeObserver(@NonNull Observer<? super T> observer) {
        AppRuntime.INSTANCE.getMainExecutor().assertMainThread("removeObserver");
        ObserverWrapper target = null;
        for (ObserverWrapper wrapper : observers) {
            if (wrapper.observer == observer) {
                target = wrapper;
                break;
            }
        }
        if (target != null) {
            observers.remove(target);
            target.activeStateChanged(false);
        }
    }

    @Override
    public void removeObservers(@NonNull LifecycleOwner owner) {
        AppRuntime.INSTANCE.getMainExecutor().assertMainThread("removeObservers");
        ArrayList<ObserverWrapper> list = new ArrayList<>();
        for (ObserverWrapper observer : observers) {
            if (observer.isAttachedTo(owner)) {
                list.add(observer);
            }
        }
        for (ObserverWrapper observerWrapper : list) {
            observers.remove(observerWrapper);
            observerWrapper.activeStateChanged(false);
        }
    }

    @Override
    public boolean hasObservers() {
        return !observers.isEmpty();
    }

    @Override
    public boolean hasActiveObservers() {
        return activeCount > 0;
    }

    private synchronized void notifyObservers(@NonNull T value) {
        for (ObserverWrapper wrapper : observers) {
            if (wrapper.shouldBeActive()) {
                //仅通知活跃中的观察者
                wrapper.observer.onChanged(value);
            }
        }
    }

    /**
     * 将这个StatelessLiveData转换为另一个StatelessLiveData.
     */
    public <O> LiveData<O> map(@NonNull final Function<T, O> mapFunction) {
        return new StatelessLiveData<O>() {
            private final Observer<T> observer = tValue -> setValue(mapFunction.apply(tValue));

            @Override
            protected void onActive() {
                StatelessLiveData.this.observeForever(observer);
            }

            @Override
            protected void onInactive() {
                StatelessLiveData.this.removeObserver(observer);
            }
        };
    }

    private class LifecycleObserver extends ObserverWrapper implements LifecycleEventObserver {

        final LifecycleOwner owner;

        private LifecycleObserver(@NotNull LifecycleOwner owner, @NotNull Observer<? super T> observer) {
            super(observer);
            this.owner = owner;
            this.owner.getLifecycle().addObserver(this);
        }

        @Override
        public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
            if (event == Lifecycle.Event.ON_DESTROY) {
                owner.getLifecycle().removeObserver(this);
                observers.remove(this);
                return;
            }
            activeStateChanged(shouldBeActive());
        }

        @Override
        boolean isAttachedTo(LifecycleOwner owner) {
            return this.owner == owner;
        }

        @Override
        boolean shouldBeActive() {
            return owner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
        }
    }

    private class ObserverWrapper {

        boolean isActive;
        final Observer<? super T> observer;

        private ObserverWrapper(@NotNull Observer<? super T> observer) {
            this.observer = observer;
        }

        boolean shouldBeActive() {
            return true;
        }

        boolean isAttachedTo(LifecycleOwner owner) {
            return false;
        }

        void activeStateChanged(boolean isActive) {
            if (this.isActive == isActive) {
                return;
            }
            this.isActive = isActive;
            boolean wasInactive = StatelessLiveData.this.activeCount == 0;
            StatelessLiveData.this.activeCount += isActive ? 1 : -1;
            if (wasInactive && isActive) {
                onActive();
            }
            if (StatelessLiveData.this.activeCount == 0 && !isActive) {
                onInactive();
            }
        }

    }

}
