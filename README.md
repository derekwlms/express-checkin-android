# Express Check-in

Event check-in for use with Breeze CHMS.

Used at [Sovereign Grace Church - Woodstock](https://sgcatlanta.org/) as a fallback for times when Breeze CHMS is unavailable or is degraded.

It uses a simpler setup - only tablets connected via Bluetooth to commodity thermal label printers. It also works in a purely offline mode - no print station or network connection required.

If the Breeze APIs are available, it will use them for lookups and check-in; otherwise, it will use an offline directory and sync check-in data once Breeze is available.

It uses the following:

- The Breeze CHMS APIs - [original (pre-aquisition)](https://app.breezechms.com/api) and [new (Tithely)](https://app.swaggerhub.com/apis/Tithe.ly/Breeze/2023-06-09)
- [TSPL (TSC Printer Language)](https://scancode.ru/upload/iblock/937/GP_1125T-Gprinter-Barcode-Printer-TSPL-Programming-Manual.pdf) [-](https://hackernoon.com/how-to-print-labels-with-tspl-and-javascript) Also [here](https://www.icintracom.biz/redazione/libretti/libretto7028-04-1.pdf)

It requires:
- One or more Android tablets. Android 8.0 and higher is required; Android 12 (API 31) is recommended.
- One or more commodity Bluetooth thermal label printers. We are currently using the [Mvgges PL925U](https://www.amazon.com/gp/product/B0DBYW5C3L/ref=ppx_yo_dt_b_asin_title_o00_s00?ie=UTF8&th=1)
- 2.25" x 4" (59mm x 102mm) labels, such as [these](https://www.amazon.com/gp/product/B0CGZWZLLP/ref=ppx_yo_dt_b_search_asin_title?ie=UTF8&th=1)


