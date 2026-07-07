FundModelSwingClient
====================

FundModelSwingClient is a Java Swing desktop program for viewing local crypto
spot OHLCV price data and local trading database tables.

What it does
------------

- Shows a BTCUSDT spot OHLCV candlestick chart.
- Uses green candles for up bars and red candles for down bars.
- Shows a separate volume area under the price candles.
- Supports multiple chart timeframes:
  1m, 5m, 15m, 30m, 1h, 2h, 4h, and D.
- Limits the chart to the latest 100 bars.
- Updates the chart live from local .bin price files.
- Shows a live indicator light.
- Provides a searchable right-side symbol table.
- Lets you click a symbol in the symbol table to load it on the chart.
- Shows Trades, PnL, and Symbol Active pages.
- Loads SQLite-backed tables only when those tabs are opened.
- Provides resizable split panes and resizable table columns.
- Provides app-wide zoom buttons to make the interface larger or smaller.
- Displays small decimal numbers without scientific notation.

Included files
--------------

- FundModelSwingClient.jar
  Runnable compiled Java program.

- Run_FundModelSwingClient.bat
  Windows double-click launcher.

- FundModelSwingClient.java
  Source code for the program.

- history_trade.sqlite
  Local history/PnL database used by the PnL page.

- tickets_hyperliquid_high_risk.sqlite
  Local open-trade database used by the Trades page.

- market_rules.sqlite
  Local market rules database used by the Trades page.

- paper_trades.sqlite
  Copied local SQLite data file.

- symbol_active.json
  Local symbol active/disabled settings.

- data_dump\daily_bin\spot\BTCUSDT
  Included BTCUSDT spot .bin OHLCV price data.

How to run
----------

Double-click:

Run_FundModelSwingClient.bat

Or run from Command Prompt or PowerShell:

java -jar FundModelSwingClient.jar

Java must be installed on Windows and available on PATH.

Data behavior
-------------

The program first looks for data beside itself in this folder.

If the local copied data exists, it uses:

- SQLite files in this folder
- price data under data_dump\daily_bin\spot
- symbol_active.json in this folder

If those local files are missing, the program falls back to the original paths:

- D:\xgb pyhton
- F:\data_dump

Quick checks
------------

These commands test that the copied data can be read:

java -jar FundModelSwingClient.jar --check BTCUSDT 1h
java -jar FundModelSwingClient.jar --dbcheck

Expected result:

- The chart check should report that 100 spot bars were plotted.
- The database check should report row counts for trades, market rules, PnL,
  and equity points.
