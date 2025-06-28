# distributed

Download JavaFX lib and move to .../src


javac -cp ".;....\src\javafx-sdk-24.0.1\lib\*" application\PeerGUIController.java
javac --module-path ".....\src\javafx-sdk-24.0.1\lib" --add-modules javafx.controls,javafx.fxml application\PeerUI.java
java --module-path ".....\src\javafx-sdk-24.0.1\lib" --add-modules javafx.controls,javafx.fxml -cp . application.PeerUI
run each in terminal, change .... by the path 
