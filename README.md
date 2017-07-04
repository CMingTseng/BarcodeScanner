# BarcodeScanner 
[![N|Solid](https://lh6.ggpht.com/-LHkE5TWqK0bWUdreGY2m28wJa9YsMvkkXiL-1u7ZRcBs2gXP8bsTTpo9IWWdY1hhaw=w300)](http://www.eoeandroid.com/thread-494931-1-1.html)
[![N|Solid](http://i.imgur.com/i5BzPzY.jpg)](http://blog.csdn.net/huang86411/article/details/27592831)
BarcodeScanner 是Android手機客戶端於二維條碼掃描的源碼，代碼的主要功能的實現使用了zxing 3.1.1的代碼，並對其進行了精簡，現在僅保存掃描和解碼部分。
現在代碼支持低版本的sdk，實現了二維碼和一維碼的掃描、從圖庫中的圖片解析一維碼和二維碼，閃光燈開啟、調焦。

# Features!
  - 支持微信式的掃描框
  - 支持低版本的SDK
  - 實現二維碼和一維碼的掃描。
  - 實現從圖庫中的圖片解析一維碼和二維碼。
  - 實現閃光燈開啟。
  - 實現調焦。
  - N多註釋


本次精簡和特性支持主要經過了以下幾個步驟:
  - 編譯zxing3.1.1代碼的core、android-core文件夾，具體是命令行窗口到文件夾路徑後，運行  mvn -DskipTests package （maven命令）編譯
  - 引入zxing 3.1.1的代碼，裁剪代碼，做完裁剪後，可以運行，支持橫屏掃描，並支持android 4.0系統以上的機子使用
  - 修改代碼支持豎屏掃描
  - 把一些僅支持高版本的sdk的代碼修改成兼容低版本的代碼：如增加了runnable.java文件，兼容 task.executeOnExecutor 
  - 完成微信掃描框
  - 修改代碼，支持從圖片解碼二維碼（核心文件是BitmapDecoder.java和BitmapLuminanceSource.java ），修改CameraManager.java，支持變焦
