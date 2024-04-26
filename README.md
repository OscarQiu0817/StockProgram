# StockProgram
看盤程式  by JavaFX

可拖動, 點兩下視窗可縮小
![image](https://github.com/OscarQiu0817/StockProgram/assets/44765969/aa6d6a3a-2abd-4c72-9bc2-dd25c0fff33e)


右鍵點選程式彈出視窗
![image](https://github.com/OscarQiu0817/StockProgram/assets/44765969/92ddc93d-2d2f-43d0-bc29-24cc7ca07534)

功能說明 : 

turn on / off fetch data => 設定是否定期抓資料 ( 預設 4.5 秒 / 次 )

turn on / off notice , 若該次抓取回來的價格, 符合檢查的條件 ( 程式內設定, 預設漲幅 >= 1.5 % ), 是否要跳出提醒視窗
![image](https://github.com/OscarQiu0817/StockProgram/assets/44765969/69403cbf-5d40-4e96-9b57-d9882ab9bb5c)

update stock list => 設定要抓取的股票 id 後, 透過此功能刷新 UI.

update ptt => 從 ptt 股版置底文章爬文, 寫到檔案內, 並支援特定 id 標註功能 ( 將ptt id 放在 pttIdHighlight.txt 內 )


-- JavaFX 範例參考來源 : https://gist.github.com/jewelsea/e231e89e8d36ef4e5d8a
