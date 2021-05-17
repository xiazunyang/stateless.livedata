# StatelessLiveData
# 当前最新版本号：[![](https://jitpack.io/v/cn.numeron/stateless.livedata.svg)](https://jitpack.io/#cn.numeron/stateless.livedata)

### 特点
* 继承自官方的LiveData
* 添加的观察者不会在被添加到LiveData的时候被回调，只会在下一次数据发生变化时回调。


### 说明
官方的LiveData会让每个观察者都记录一个version的值，当观察者中记录的version小于LiveData的version时，会在观察者刚添加到LiveData中时执行一次回调，此时就发生某人口中的“数据倒灌”现象。
这里直接继承自官方的LiveData，然后重写了所有的公开的方法，所有的操作都在StatelessLiveData中处理，LiveData不会感知任何数据，并且处理了以上提到的问题，以满足部分情况下的需求。


### 引入

1.  在你的android工程的根目录下的build.gradle文件中的适当的位置添加以下代码：
```groovy
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

2.  在你的android工程中对应的android模块的build.gradle文件中的适当位置添加以下代码：
```groovy
implementation 'cn.numeron:stateless.livedata:latest_version'
```
