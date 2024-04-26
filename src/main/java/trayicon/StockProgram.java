package trayicon;

import java.awt.TrayIcon;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.imageio.ImageIO;

import javafx.scene.input.MouseButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.StageStyle;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;


// special thanks to jewelsea's excellent work : https://gist.github.com/jewelsea/e231e89e8d36ef4e5d8a
public class StockProgram  extends Application {

    // application stage is stored so that it can be shown and hidden based on system tray icon operations.
    private Stage stage;

    // a timer allowing the tray icon to provide a periodic notification event.
    private Timer notificationTimer = new Timer();

    // 存放 label 的 list ( 方便更新元件 )
    private List<Label> labelList;

    private List<CustomHbox> hboxList;

    // 存放股票清單的 list
    private List<String> stockList;

    private VBox content;

    private int defaultWidth = 400;

    // 是否打開提醒的 flag
    private boolean noticeOn = false;
    private boolean connectionOn = false;

    static Map<String, String> NumPriceMapping = new HashMap<>();

    private double xOffset = 0;
    private double yOffset = 0;

    private int[] orderWay = new int[]{0,1,2};
    private int currentOrderWay;
    private String currentOrderColumn = "";

    class CustomHbox implements Comparator{

        public HBox hBox;

        public String stockNum;

        public String stockName;

        public String price;

        public String diff;

        public String change;

        @Override
        public int compare(Object o1, Object o2) {
            return 0;
        }
    }

    @Override
    public void start(Stage stage) throws Exception {

        // init label List
        labelList = new ArrayList<Label>();
        hboxList = new ArrayList<CustomHbox>();

        // stores a reference to the stage.
        this.stage = stage;

        // instructs the javafx system not to exit implicitly when the last application window is shut.
        Platform.setImplicitExit(false);

        // sets up the tray icon (using awt code run on the swing thread).
        javax.swing.SwingUtilities.invokeLater(this::addAppToTray);

        // create the layout for the javafx stage.
        StackPane layout = new StackPane(createContent());
        layout.setStyle(
                "-fx-background-color: rgba(0, 0, 0, 0.7);"
        );
        layout.setPrefSize(defaultWidth, stockList.size() * 20 + 40);
        layout.setBackground(Background.EMPTY);

        // this dummy app just hides itself when the app screen is clicked.
        // a real app might have some interactive UI and a separate icon which hides the app window.
        layout.setOnMouseClicked(event ->{
            if(event.getButton().equals(MouseButton.PRIMARY) && event.getClickCount() == 2) {
                stage.hide();
            }}
        );


        // 當 設定 stage.initStyle(StageStyle.TRANSPARENT) 的時候會把視窗按鈕也弄不見，所以需要重寫滑鼠事件
        // 滑鼠按下時的事件處理
        layout.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        // 拖動時的事件處理
        layout.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });



        // a scene with a transparent fill is necessary to implement the translucent app window.
        Scene scene = new Scene(layout);
        scene.setFill(Color.TRANSPARENT);


        // 設置下方應用程式不同圖案
        stage.getIcons().add(new javafx.scene.image.Image(this.getClass().getResourceAsStream("/img/stockUp.png")));

        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setScene(scene);

        // 出現在右下角
        Rectangle2D primaryScreenBounds = Screen.getPrimary().getVisualBounds();
        //set Stage boundaries to the lower right corner of the visible bounds of the main screen
        stage.setX(primaryScreenBounds.getMinX() + primaryScreenBounds.getWidth() - 400);
        stage.setY(primaryScreenBounds.getMinY() + primaryScreenBounds.getHeight() - 300);



    }

    /**
     * Sets up a system tray icon for the application.
     */
    private void addAppToTray() {
        try {
            // ensure awt toolkit is initialized.
            java.awt.Toolkit.getDefaultToolkit();

            // app requires system tray support, just exit if there is no support.
            if (!java.awt.SystemTray.isSupported()) {
                System.out.println("No system tray support, application exiting.");
                Platform.exit();
            }

            // set up a system tray icon.
            java.awt.SystemTray tray = java.awt.SystemTray.getSystemTray();
//            URL imageLoc = new URL(
//                    iconImageLoc
//            );

            URL imageLoc = StockProgram.class.getResource("/img/stockUp.png");
            java.awt.Image image = ImageIO.read(imageLoc);
            java.awt.TrayIcon trayIcon = new java.awt.TrayIcon(image);

            // 自動調整圖片大小
            trayIcon.setImageAutoSize(true);

            // if the user double-clicks on the tray icon, show the main app stage.
            trayIcon.addActionListener(event -> Platform.runLater(this::showStage));


            // added by Oscar ========================================================================

            // toolTip
            trayIcon.setToolTip("股票工具");

            java.awt.MenuItem toggleNoticeItem = new java.awt.MenuItem("turn on notice");
            toggleNoticeItem.addActionListener(event -> {

                if(noticeOn) {
                    noticeOn = false;
                    toggleNoticeItem.setLabel("turn on notice");
                }else {
                    noticeOn = true;
                    toggleNoticeItem.setLabel("turn off notice");
                }
            });

            java.awt.MenuItem updateStockList = new java.awt.MenuItem("update Stock List");
            updateStockList.addActionListener(event -> {
                try {
                    updateStockList();
                } catch (IOException e) {
                    System.out.println("ptt 網頁刷新失敗!");
                    e.printStackTrace();
                }
            });

            java.awt.MenuItem updatePttItem = new java.awt.MenuItem("update PTT");
            updatePttItem.addActionListener(event -> {
                try {
                    updatePttHtml();
                } catch (IOException e) {
                    System.out.println("ptt 網頁刷新失敗!");
                    e.printStackTrace();
                }
            });

            java.awt.MenuItem toggleConnection = new java.awt.MenuItem("turn on fetch data");
            toggleConnection.addActionListener(event -> {

                if(connectionOn) {
                    connectionOn = false;
                    toggleConnection.setLabel("turn on fetch data");
                }else {
                    connectionOn = true;
                    toggleConnection.setLabel("turn off fetch data");
                }
            });


            // create a timer which periodically displays a notification message.
            notificationTimer.schedule(

                    new TimerTask() {
                        @Override
                        public void run() {
                            javax.swing.SwingUtilities.invokeLater(() ->

                                    Platform.runLater(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {

                                                if(updateStageHeight){
                                                    updateStageHeight = false;
                                                    stage.setHeight(stockList.size() * 20 + 110);

                                                }

                                                if(connectionOn){
                                                    System.out.println("資料更新時間 : " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
                                                    getStockInfo(stockList);
                                                }

                                                if(noticeOn) {
                                                    checkNotice(trayIcon);
                                                }

                                            } catch (IOException e) {
                                                e.printStackTrace();
                                                connectionOn = false;
                                                System.out.println("因連線失敗自動關閉連線避免積累. : " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
                                            } catch (Exception ex){
                                                ex.printStackTrace();
                                                connectionOn = false;
                                                System.out.println("因連線失敗自動關閉連線避免積累. : " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
                                            }
                                        }
                                    })
                            );
                        }
                    },
                    10_000,
                    4500
            ); // 數字在 java 7 之後可以加底線，編譯時會自動忽略 → 增加可讀性。*/



            // added by Oscar ========================================================================


            // to really exit the application, the user must go to the system tray icon
            // and select the exit option, this will shutdown JavaFX and remove the
            // tray icon (removing the tray icon will also shut down AWT).
            java.awt.MenuItem exitItem = new java.awt.MenuItem("Exit");
            exitItem.addActionListener(event -> {
                notificationTimer.cancel();
                Platform.exit();
                tray.remove(trayIcon);
            });

            // setup the popup menu for the application.
            final java.awt.PopupMenu popup = new java.awt.PopupMenu();
            popup.add(toggleConnection);
            popup.add(toggleNoticeItem);
            popup.add(updateStockList);
            popup.add(updatePttItem);
            popup.addSeparator();
            popup.add(exitItem);
            trayIcon.setPopupMenu(popup);

            tray.add(trayIcon);

        } catch (java.awt.AWTException | IOException e) {
            System.out.println("Unable to init system tray");
            e.printStackTrace();
        }
    }

    // 檢查當前值是否須提醒
    private void checkNotice(TrayIcon trayIcon) {

        StringBuffer msg = new StringBuffer("");

        for(CustomHbox hbox : hboxList) {

            String changeStr = hbox.change;
            String diffStr = hbox.diff;

            double d_change = Math.abs(Double.valueOf(changeStr));
            double d_diff = Math.abs(Double.valueOf(diffStr));

            // 漲跌幅 > 1.5 % or 漲跌 > 0.5 就提醒
//            if(d_change >= 1.5 || d_diff >= 0.5) {
//                msg.append(label.getText() + "\n");
//            }

//            if(d_change >= 1.5) {
//                msg.append(label.getText() + "\n");
//            }
            if(d_change >= 1.5) {
                msg.append( String.format("%s %s | %s | %s | %s", hbox.stockNum, hbox.stockName,
                        Double.parseDouble(new DecimalFormat("#0.0000").format(Double.parseDouble(hbox.price))),
                        hbox.diff , hbox.change+"%\n"));
            }

        }

        if(msg.toString().length() > 0) {
            trayIcon.displayMessage("波動提醒",
                    msg.toString(), TrayIcon.MessageType.WARNING);
        }

    }

    // 取得欲查詢股票清單
    private List<String> getStockList() throws IOException{

        // 特定 ID 標註 - 改用讀檔方式
        File fStock = new File("data\\stock.txt");
        BufferedReader brStock = new BufferedReader(new FileReader(fStock));
        List<String> stockList = new ArrayList<String>();
        String line;
        while((line = brStock.readLine()) != null) {
            System.out.println(line);
            stockList.add(line);
        }

        brStock.close();

        return stockList;
    }

    private void initContent(){

        content.getChildren().clear();

        Label label1 = new Label("代  號");
        Label label2 = new Label("名  稱");
        Label label3 = new Label("股  價");
        Label label4 = new Label("漲  跌");
        Label label5 = new Label("幅  度");

        StringBuilder sbStyle = new StringBuilder();
        sbStyle.append("-fx-text-fill: rgb(255,255,255);");
        sbStyle.append("-fx-font-size: 20px;");
        label1.setStyle(sbStyle.toString());
        label2.setStyle(sbStyle.toString());
        label3.setStyle(sbStyle.toString());
        label4.setStyle(sbStyle.toString());
        label5.setStyle(sbStyle.toString());
        HBox hbox = new HBox(label1, label2, label3, label4, label5);
        hbox.setSpacing(20);

        for(Node label : hbox.getChildren()){
            String text = ((Label) label).getText();
            label.setOnMouseClicked(event ->{
                    if(! text.equals(currentOrderColumn)){
                        currentOrderColumn = text;
                        currentOrderWay = orderWay[1];
                    }else{
                        if(currentOrderWay == 2)
                            currentOrderWay = 0;
                        else
                            currentOrderWay ++;
                    }
                }
            );
        }

        if( currentOrderWay != 0 ){
            int index = 0;
            switch (currentOrderColumn){
                case "股  價":
                    index = 2;
                    break;
                case "漲  跌":
                    index = 3;
                    break;
                case "幅  度":
                    index = 4;
                    break;
            }
            ((Label)(hbox.getChildren().get(index))).setText(currentOrderColumn +
                    (currentOrderWay == 1 ? "↓" : "↑"));
        }


        content.getChildren().add(hbox);
    }

    // 取得股票資料
    private VBox getStockInfo(List<String> stockList) throws IOException {

        if(content == null){
            content = new VBox();
        }

        hboxList.clear();

        String preUrl = "https://mis.twse.com.tw/stock/api/getStockInfo.jsp?ex_ch=" ;
        String lastUrl = ".tw";

        String url = "";
        // 讀取股票清單
        for(int i = 0 ; i < stockList.size() ; i ++) {
            String stockID = stockList.get(i);
            url += "tse_" + stockID + lastUrl;

            if(i != stockList.size() -1) {
                url += "|";
            }
        }

        // 連到目前API
        Document doc = Jsoup.connect(preUrl + url).validateTLSCertificates(false).get(); // 關掉 SSL 驗證

        // 取得回傳資料
        Element body = doc.body();
        String stockInfo = body.text();

        // 拆解 json 字串
        JSONObject allStockInfo = new JSONObject(stockInfo);
        JSONArray msgArray = allStockInfo.getJSONArray("msgArray");

        //['c','n','z','tv','v','o','h','l','y'] 分別代表 ['股票代號','公司簡稱','當盤成交價','當盤成交量','累積成交量','開盤價','最高價','最低價','昨收價']
        for(int i = 0 ; i < msgArray.length() ; i ++ ){

            JSONObject stock = msgArray.getJSONObject(i);

            String stockNum = (String) stock.get("c");
            String stockName = (String) stock.get("n");
            String nowPrice = (String) stock.get("z");
            String yesterdayPrice = (String) stock.get("y");
            if(nowPrice.equals("-")){
                if( ! NumPriceMapping.containsKey(stockNum) ) {
                    NumPriceMapping.put(stockNum, yesterdayPrice);
                    NumPriceMapping.put(stockNum+"_highlightCount", "0");
                }
            }else{
                NumPriceMapping.put(stockNum, nowPrice);
                NumPriceMapping.put(stockNum+"_highlightCount", "3");
            }
            nowPrice = NumPriceMapping.get(stockNum);
            int highlightCount = Integer.parseInt(NumPriceMapping.get(stockNum+"_highlightCount"));

            Double difference = (Double.parseDouble(nowPrice) - Double.parseDouble(yesterdayPrice)) ;
            Double change = difference / Double.parseDouble(yesterdayPrice) ;

            DecimalFormat df = new DecimalFormat("#0.0000");
            double d_diff = Double.parseDouble(df.format(difference));
            double d_change = Double.parseDouble(df.format(change));
            // 下跌用綠色顯示
            boolean greenFlag = false;
            if(d_diff < 0) {
                d_diff = Math.abs(d_diff);
                d_change = Math.abs(d_change);
                greenFlag = true;
            }

            String diffStr = String.valueOf(d_diff).length() == 3 ? String.valueOf(d_diff) + "0" : String.valueOf(d_diff);

            String arrow = greenFlag ? "  ▼ " : "  ▲ " ;
            String labelText = stockName + "("+stockNum + ") : " + Double.parseDouble(df.format(Double.parseDouble(nowPrice)))  + arrow + diffStr + " " +
                    Double.parseDouble(df.format((d_change * 100))) + "%";

            Label label1 = new Label(stockNum);
            Label label2 = new Label(stockName);
            Label label3 = new Label(String.valueOf(Double.parseDouble(df.format(Double.parseDouble(nowPrice)))));
            Label label4 = new Label(arrow + diffStr);
            Label label5 = new Label(Double.parseDouble(df.format((d_change * 100))) + "%");
            label1.setWrapText(true);
            label2.setWrapText(true);
            label3.setWrapText(true);
            label4.setWrapText(true);
            label5.setWrapText(true);

            HBox hbox = new HBox(label1, label2, label3, label4, label5);
            CustomHbox customHbox = new CustomHbox();
            customHbox.hBox = hbox;
            customHbox.stockNum = stockNum;
            customHbox.stockName = stockName;
            customHbox.price = nowPrice;
            customHbox.diff = String.valueOf(Double.parseDouble(df.format(difference)));
            customHbox.change = String.valueOf(Double.parseDouble(df.format((Double.parseDouble(df.format(change)) * 100))));

            hboxList.add(customHbox);

            StringBuilder sbStyle = new StringBuilder();
            if(greenFlag) {
                sbStyle.append("-fx-text-fill: lightgreen;");
            }else {
                sbStyle.append("-fx-text-fill: rgb(255,60,60);");
            }
            if(highlightCount > 0){
                sbStyle.append("-fx-font-weight: bold;-fx-font-size: 20px;");
                highlightCount --;
                NumPriceMapping.put(stockNum+"_highlightCount", String.valueOf(highlightCount));
            }else{
                sbStyle.append("-fx-font-size: 18px;");
            }
            label1.setStyle(sbStyle.toString());
            label2.setStyle(sbStyle.toString());
            label3.setStyle(sbStyle.toString());
            label4.setStyle(sbStyle.toString());
            label5.setStyle(sbStyle.toString());

            // 如果文字太長動態調整第一次預設寬度
            if( labelText.length() >=  20 && labelText.length() * 15 > 400 ){
                defaultWidth = defaultWidth < labelText.length() * 16 ? labelText.length() * 17 : defaultWidth;
            }
        }

        // 客製化排序方式 ( 排 labelList ), 再放到 content.
        if( currentOrderWay != 0 ){
            Collections.sort(hboxList, (o1, o2) -> {

                double result = 0;

                if( currentOrderColumn.equals("股  價")){
                    result = Double.valueOf(o1.price) - Double.valueOf(o2.price);
                }else if( currentOrderColumn.equals("漲  跌")){
                    result = Double.valueOf(o1.diff) - Double.valueOf(o2.diff);
                }else if( currentOrderColumn.equals("幅  度")){
                    result = Double.valueOf(o1.change) - Double.valueOf(o2.change);
                }

                if(result > 0){
                    result = 1;
                }else if (result == 0){
                    result = 0;
                }else{
                    result = -1;
                }

                if( currentOrderWay == 1 ){
                    result *= -1;
                }
                return (int) result;
            });
        }


        // 重設 content
        initContent();
        ((Label)((HBox)content.getChildren().get(0)).getChildren().get(0)).setPrefWidth(defaultWidth * 0.17);
        ((Label)((HBox)content.getChildren().get(0)).getChildren().get(1)).setPrefWidth(defaultWidth * 0.33);
        ((Label)((HBox)content.getChildren().get(0)).getChildren().get(2)).setPrefWidth(defaultWidth * 0.18);
        ((Label)((HBox)content.getChildren().get(0)).getChildren().get(3)).setPrefWidth(defaultWidth * 0.15);
        ((Label)((HBox)content.getChildren().get(0)).getChildren().get(4)).setPrefWidth(defaultWidth * 0.15);

        for(CustomHbox hbox : hboxList){
            hbox.hBox.setSpacing(20); // 設置間距
            content.getChildren().add(hbox.hBox);

            ((Label)hbox.hBox.getChildren().get(0)).setPrefWidth(defaultWidth * 0.17);
            ((Label)hbox.hBox.getChildren().get(1)).setPrefWidth(defaultWidth * 0.34);
            ((Label)hbox.hBox.getChildren().get(2)).setPrefWidth(defaultWidth * 0.16);
            ((Label)hbox.hBox.getChildren().get(3)).setPrefWidth(defaultWidth * 0.18);
            ((Label)hbox.hBox.getChildren().get(4)).setPrefWidth(defaultWidth * 0.15);
        }

        return content;
    }

    static boolean updateStageHeight = false;
    private void updateStockList() throws IOException {
        stockList = getStockList();
        updateStageHeight = true;
    }

    // 更新 ptt 貼文
    private void updatePttHtml() throws IOException {

        // 找到 PTT 股版 置頂貼文 ( index.html > 最新 、 置頂 > 最下面)
        Document doc = Jsoup.connect("https://www.ptt.cc/bbs/Stock/index.html").get();
        Elements articleLine = doc.select(".r-ent .title a");

        String topHref = articleLine.last().absUrl("href");
        System.out.println(topHref);

        doc = Jsoup.connect(topHref).get();

        // 紀錄推文用
        StringBuffer sb = new StringBuffer();

        // 特定 ID 標註 - 改用讀檔方式
        File fid = new File("data\\pttIdHighlight.txt");
        BufferedReader brid = new BufferedReader(new FileReader(fid));
        String idLine = null;
        List<String> idList = new ArrayList<String>();
        while((idLine = brid.readLine()) != null) {
            idList.add(idLine);
        }
        brid.close();
//			String[] ids = new String[] {"zesonpso", "f204137", "tenghui"}; // 鬆哥 、 fj 、 T大

        // 推文
        Elements replyLine = doc.select("#main-content .push");
        for(Element reply : replyLine) {

            // 推文子元素
            Elements replyElement = reply.children();
//    				Elements replyElement = reply.getAllElements(); // 包含自己
//    				sb.append(replyElement.get(0)+"\n"); // 會拿到整個 div 元素，但這樣不好對個別 span 做顏色調整 ( e.g. 特定 id 檢測

            StringBuffer tmpLine = new StringBuffer();
            boolean highLight = false;

            sb.append("<div class='push'>");
            for(Element element : replyElement) {

                String content = element.toString();
                tmpLine.append(content + "\n");

                if(content.contains("push-userid")) {

                    for(String id : idList) {
                        if(content.contains(id)) {
                            highLight = true;
                            break;
                        }
                    }

                }

            }
            if(highLight) {
                tmpLine = new StringBuffer(tmpLine.toString().replaceAll("class=\"", "class=\" spe-id "));
            }

            sb.append(tmpLine );
            sb.append("</div> \n");

        }

        // 判斷是否在 body 區塊。 另一方面作為避免重複的 flag
        boolean inBody = false;
        StringBuffer newHtml = new StringBuffer();

        File f = new File("data\\pttStock.html");
        BufferedReader br = new BufferedReader(new FileReader(f));

        String line = null;
        while((line = br.readLine()) != null) {

            if(!inBody) {
                newHtml.append(line+"\n");
            }

            if(line.contains("<body>")) {
                inBody = true;
            }

            if(line.contains("</body>")) {
                inBody = false;
                newHtml.append(sb);
                newHtml.append("\n"+line+"\n");
            }

        }

        BufferedWriter bw = new BufferedWriter(new FileWriter(f));
        bw.write(newHtml.toString());

        bw.close();
        br.close();

    }


    /**
     * 點兩下會出現的內容
     * For this dummy app, the (JavaFX scenegraph) content, just says "hello, world".
     * A real app, might load an FXML or something like that.
     *
     * @return the main window application content.
     * @throws IOException
     */
    private Node createContent() throws IOException {

        stockList = getStockList();

        content = getStockInfo(stockList);

        content.setAlignment(Pos.CENTER);
        content.setBackground(Background.EMPTY);

        return content;
    }

    /**
     * Shows the application stage and ensures that it is brought ot the front of all stages.
     */
    private void showStage() {
        if (stage != null) {
            stage.setTitle("股票程式 v1.1");
            stage.show();
            stage.toFront();
        }
    }

    public static void main(String[] args) throws IOException, java.awt.AWTException {
        launch(args);
    }

}
