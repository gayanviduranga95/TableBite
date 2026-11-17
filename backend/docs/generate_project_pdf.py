from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import mm
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak,
    KeepTogether, ListFlowable, ListItem, Flowable
)
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.pdfmetrics import registerFontFamily
from datetime import date

OUTPUT = r"e:\Networking_Project\TableBite\TableBite_Project_Overview.pdf"

# Fonts (built-in PDF fonts for reliable cross-platform generation)
styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name="CoverTitle", fontName="Helvetica-Bold", fontSize=28, leading=34, alignment=TA_CENTER, textColor=colors.HexColor("#FFFFFF"), spaceAfter=12*mm))
styles.add(ParagraphStyle(name="CoverSub", fontName="Helvetica", fontSize=12, leading=18, alignment=TA_CENTER, textColor=colors.HexColor("#E8ECF2")))
styles.add(ParagraphStyle(name="Title2", fontName="Helvetica-Bold", fontSize=20, leading=24, textColor=colors.HexColor("#16324F"), spaceBefore=4*mm, spaceAfter=5*mm))
styles.add(ParagraphStyle(name="Heading", fontName="Helvetica-Bold", fontSize=13, leading=16, textColor=colors.HexColor("#16324F"), spaceBefore=4*mm, spaceAfter=2*mm))
styles.add(ParagraphStyle(name="Body", fontName="Helvetica", fontSize=9.2, leading=13.2, textColor=colors.HexColor("#263238"), spaceAfter=2*mm))
styles.add(ParagraphStyle(name="Small", fontName="Helvetica", fontSize=7.6, leading=10.5, textColor=colors.HexColor("#37474F")))
styles.add(ParagraphStyle(name="CodeBlock", fontName="Courier", fontSize=7.2, leading=9.5, textColor=colors.HexColor("#E6EDF3"), backColor=colors.HexColor("#17202A"), borderPadding=5, leftIndent=4, rightIndent=4, spaceBefore=2*mm, spaceAfter=3*mm))
styles.add(ParagraphStyle(name="Callout", fontName="Helvetica", fontSize=8.6, leading=12, textColor=colors.HexColor("#0B3C5D"), backColor=colors.HexColor("#EAF4FB"), borderColor=colors.HexColor("#4B8BBE"), borderWidth=0.7, borderPadding=7, spaceBefore=2*mm, spaceAfter=3*mm))

class ArchitectureDiagram(Flowable):
    def __init__(self, width=170*mm, height=88*mm):
        super().__init__()
        self.width, self.height = width, height

    def draw(self):
        c = self.canv
        w, h = self.width, self.height
        navy = colors.HexColor("#16324F")
        blue = colors.HexColor("#4B8BBE")
        light = colors.HexColor("#EAF4FB")
        green = colors.HexColor("#59A14F")

        def box(x, y, bw, bh, title, subtitle="", fill=light):
            c.setFillColor(fill); c.setStrokeColor(navy); c.setLineWidth(0.8)
            c.roundRect(x, y, bw, bh, 4, fill=1, stroke=1)
            c.setFillColor(navy); c.setFont("Helvetica-Bold", 8)
            c.drawCentredString(x+bw/2, y+bh-13, title)
            c.setFont("Helvetica", 6.8)
            for i, line in enumerate(subtitle.split("\n")):
                c.drawCentredString(x+bw/2, y+bh-24-i*9, line)

        def arrow(x1,y1,x2,y2):
            c.setStrokeColor(blue); c.setLineWidth(1.2)
            c.line(x1,y1,x2,y2)
            c.setFillColor(blue)
            import math
            ang=math.atan2(y2-y1,x2-x1)
            for sign in (1,-1):
                pass

        box(0, h-32*mm, 46*mm, 25*mm, "Phone / Browser", "Vue 3 SPA\nCustomer, Kitchen, Staff", colors.HexColor("#F4F8FB"))
        box(62*mm, h-32*mm, 46*mm, 25*mm, "Vite development proxy", "5173 / 5174\nHTTPS for phone camera", colors.HexColor("#FFF4DB"))
        box(124*mm, h-32*mm, 46*mm, 25*mm, "Java backend", "Three network servers", colors.HexColor("#E7F3E3"))

        c.setStrokeColor(blue); c.setLineWidth(1.2)
        c.line(46*mm,h-19.5*mm,62*mm,h-19.5*mm)
        c.line(108*mm,h-19.5*mm,124*mm,h-19.5*mm)

        box(124*mm, h-58*mm, 46*mm, 20*mm, "HTTP API :8080", "/api, /uploads\n16-thread pool")
        box(124*mm, h-82*mm, 46*mm, 20*mm, "WebSocket :8081", "Real-time events\nsubscriber groups")
        box(124*mm, h-106*mm, 46*mm, 20*mm, "TCP :5000", "NDJSON messages\nbounded worker pool")

        c.setStrokeColor(navy)
        c.line(147*mm,h-32*mm,147*mm,h-38*mm)
        c.line(147*mm,h-38*mm,147*mm,h-86*mm)

        box(55*mm, h-83*mm, 50*mm, 25*mm, "Services", "Auth · Menu · Table\nOrder · Payment · Settings")
        box(55*mm, h-112*mm, 50*mm, 25*mm, "Repositories / JDBC", "Prepared statements\nTransactions and locks")
        box(55*mm, h-141*mm, 50*mm, 25*mm, "PostgreSQL", "tablebite_db\nSchema migrations")
        c.setStrokeColor(blue); c.setLineWidth(1.2)
        c.line(80*mm,h-83*mm,80*mm,h-87*mm)
        c.line(80*mm,h-112*mm,80*mm,h-117*mm)

        c.setFont("Helvetica", 7); c.setFillColor(green)
        c.drawString(2*mm, 2*mm, "Protocol flow: Browser / phone  <->  Vite  <->  Java  <->  PostgreSQL")

def P(text, style="Body"):
    return Paragraph(text, styles[style])

def bullets(items):
    return ListFlowable([ListItem(P(x)) for x in items], bulletType='bullet', leftIndent=14, bulletFontName="Helvetica", bulletFontSize=7)

def code(text):
    return P(text.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br/>"), "CodeBlock")

def section(title):
    return [PageBreak(), P(title, "Title2")]

def table(data, widths=None, small=False):
    style_name = "Small" if small else "Body"
    converted=[]
    for row in data:
        converted.append([P(str(v), style_name) if not isinstance(v, Flowable) else v for v in row])
    t=Table(converted, colWidths=widths, repeatRows=1, hAlign='LEFT')
    t.setStyle(TableStyle([
        ('BACKGROUND',(0,0),(-1,0),colors.HexColor("#16324F")),
        ('TEXTCOLOR',(0,0),(-1,0),colors.white),
        ('FONTNAME',(0,0),(-1,0),'Helvetica-Bold'),
        ('FONTNAME',(0,1),(-1,-1),'Helvetica'),
        ('GRID',(0,0),(-1,-1),0.35,colors.HexColor("#B0BEC5")),
        ('VALIGN',(0,0),(-1,-1),'TOP'),
        ('ROWBACKGROUNDS',(0,1),(-1,-1),[colors.white,colors.HexColor("#F5F7F9")]),
        ('LEFTPADDING',(0,0),(-1,-1),5),('RIGHTPADDING',(0,0),(-1,-1),5),
        ('TOPPADDING',(0,0),(-1,-1),5),('BOTTOMPADDING',(0,0),(-1,-1),5),
    ]))
    return t

story=[]

# Cover
cover=Table([[P("TABLEBITE", "CoverTitle")],[P("Complete Project Guide & Network Programming Explained", "CoverSub")],[P("Restaurant Ordering System<br/>Java • Vue • PostgreSQL • HTTP • WebSocket • TCP", "CoverSub")]], colWidths=[170*mm], rowHeights=[55*mm,22*mm,25*mm])
cover.setStyle(TableStyle([
    ('BACKGROUND',(0,0),(-1,-1),colors.HexColor("#16324F")),
    ('VALIGN',(0,0),(-1,-1),'MIDDLE'),
    ('BOX',(0,0),(-1,-1),0,colors.HexColor("#16324F")),
]))
story += [Spacer(1,25*mm), cover, Spacer(1,12*mm), P("Prepared 18 November 2026", "Small")]
story.append(PageBreak())

story += [P("Contents", "Title2")]
contents=[
"1. Project overview", "2. Application features", "3. Overall architecture",
"4. Network programming basics", "5. Port allocation and start-up",
"6. HTTP REST API: port 8080", "7. WebSocket: port 8081",
"8. Raw TCP server: port 5000", "9. Frontend networking",
"10. Ordering data flow", "11. Concurrency and resource limits",
"12. Database and transactions", "13. Security", "14. Setup and running",
"15. Troubleshooting", "16. Glossary and practical summary"
]
story += [bullets(contents), Spacer(1,5*mm), P("This guide explains the implementation as it exists in the workspace. It combines project documentation with source-code behavior and is suitable for a software/networking assignment report.", "Callout")]

story += section("1. Project Overview")
story += [P("TableBite is a full-stack restaurant ordering system where customers use QR codes to identify a table, place dine-in or takeaway orders, and track order progress. Kitchen staff accept and prepare dishes, waiters serve completed orders and record payments, and administrators manage menus, tables, users and reports."),
P("The system demonstrates three network communication styles simultaneously:", "Heading"),
bullets(["HTTP/REST at port 8080 for normal request–response operations.","WebSocket at port 8081 for push notifications and live updates.","Raw TCP with newline-delimited JSON at port 5000 for a custom protocol."]),
P("Technology summary", "Heading")]
story.append(table([
["Area","Technology"],["Backend","Java 21, JDK HttpServer, Java-WebSocket, JDBC, Jackson"],["Database","PostgreSQL 13+"],["Frontend","Vue 3, Vite, Vue Router, Tailwind CSS"],["Build","Maven and npm"],["Network ports","8080 HTTP; 8081 WebSocket; 5000 TCP; 5173/5174 Vite"]], [42*mm,128*mm], True))

story += section("2. Application Features")
story += [P("Customer features", "Heading")]
story += [bullets(["QR table selection and manual table-number fallback.","Menu search, category and dietary filters, cart with persisted quantities and notes.","Dine-in or takeaway checkout and server-calculated totals.","Cash/pay-at-counter choices; live status tracking and cancellation while pending.","Order history kept on the browser and private tracking through a secret token."]), P("Staff features", "Heading")]
story += [bullets(["Kitchen queue with new, accepted, cooking and ready states.","Waiter/floor queue for ready-to-serve and awaiting-payment orders.","Admin dashboard, menu/category management, table/QR management, payments and staff accounts."]), P("Roles", "Heading")]
story.append(table([["Role","Main responsibilities"],["CUSTOMER","Place, track and cancel eligible own orders"],["KITCHEN","Accept, prepare and mark orders ready"],["WAITER","Serve, complete and confirm payments"],["ADMIN","All kitchen/floor operations plus management and reporting"]], [42*mm,128*mm], True))

story += section("3. Overall Architecture")
story += [P("The project uses a layered architecture. The frontend calls the backend through HTTP and WebSocket. The backend parses and validates requests, applies business rules, queries PostgreSQL through JDBC repositories, and sends live events back to subscribed clients.", "Body"), ArchitectureDiagram(), Spacer(1,4*mm), P("Layer responsibilities", "Heading")]
story.append(table([
["Layer","Package","Responsibility"],["Transport","server / server.api","HTTP, WebSocket, raw TCP, request parsing"],["Services","service","Authentication, pricing, validation, status rules"],["Persistence","database","JDBC repositories, migrations, transactions"],["Model","model","Orders, menus, users and enums"],["Payment","payment","Provider integration interface"],["Utilities","util","JSON, logging, password and token security"]], [34*mm,43*mm,93*mm], True))

story += section("4. Network Programming Basics")
story += [P("Sockets and ports", "Heading"), P("A socket is an endpoint for network communication. A TCP connection is usually identified by a four-part tuple: source IP, source port, destination IP and destination port. A server first creates a listening socket, binds it to an address and port, and calls accept() to receive a client connection. Binding reserve means that port is allocated for that server process on the local machine."),
P("Transport choices", "Heading")]
story.append(table([
["Protocol","Used for","Characteristics"],["TCP","HTTP, WebSocket and custom TCP","Reliable, ordered byte stream; connection-oriented"],["HTTP","REST API","Request–response messages over TCP"],["WebSocket","Live notifications","Long-lived TCP connection carrying bidirectional frames"],["UDP","Not used here","Connectionless and does not provide TCP reliability"]], [32*mm,55*mm,83*mm], True))
story += [P("Why the project uses three servers", "Heading"), bullets(["HTTP is ideal for CRUD operations such as creating orders or updating menu items.","WebSocket is ideal for server-initiated notifications because HTTP alone does not maintain a live channel.","Raw TCP demonstrates a custom application protocol and direct socket programming independent of HTTP methods and headers."]), P("IP addresses and loopback", "Callout")]

story += section("5. Port Allocation and Start-up")
story += [P("Main.java is the entry point. It reads environment variables, migrates the database, creates authentication services, starts the HTTP server, starts the WebSocket server and then starts the TCP server.", "Body")]
story.append(table([
["Service","Default","Environment variable","Binding"],["HTTP REST","8080","HTTP_PORT","All interfaces by JDK HttpServer"],["WebSocket","8081","WEBSOCKET_PORT","All interfaces for Java-WebSocket"],["Raw TCP","5000","TCP_PORT","127.0.0.1 by default"],["Vite HTTP","5173","FRONTEND_HTTP_PORT","LAN/Wi-Fi because host=true"],["Vite HTTPS","5174","FRONTEND_HTTPS_PORT","LAN/Wi-Fi; camera access"]], [38*mm,22*mm,55*mm,55*mm], True))
story += [P("TCP_HOST controls the custom TCP binding. 127.0.0.1 means only programs on the same computer can connect. Setting a LAN address or 0.0.0.0 makes the server reachable across networks, but plain text and firewall exposure must be considered.", "Callout")]
story += [P("Start-up sequence", "Heading"), code("1. Read HTTP_PORT, WEBSOCKET_PORT and TCP_PORT\n2. Run PostgreSQL migrations if DB_AUTO_MIGRATE is enabled\n3. Bootstrap development accounts\n4. Start HttpApiServer on 8080\n5. Start LiveWebSocketServer on 8081\n6. Start TcpServer on 5000\n7. Register a JVM shutdown hook\n8. Start Vite frontend servers (5173 and 5174)")]

story += section("6. HTTP REST API: port 8080")
story += [P("The JDK HttpServer creates a listening socket and a fixed thread pool of 16 worker threads. Every request to /api is dispatched through the Router and ApiDispatcher, and /uploads is handled separately by UploadStore."),
P("Request structure", "Heading")]
story += [code("GET /api/menu\nAuthorization: Bearer <session-token>\n\nPOST /api/orders\nContent-Type: application/json\n\n{\n  \"orderType\": \"DINE_IN\",\n  \"tableToken\": \"tb-...\",\n  \"items\": [{\"menuItemId\": 7, \"quantity\": 2}],\n  \"paymentMethod\": \"PAY_AT_COUNTER\"\n}")]
story += [P("Public examples", "Heading")]
story.append(table([
["Method","Endpoint","Purpose"],["GET","/api/settings","Restaurant settings and feature flags"],["GET","/api/menu","Customer menu"],["GET","/api/tables/{qrToken}","Resolve a scanned table token"],["POST","/api/orders","Create an order"],["GET","/api/orders/{id}?token=...","Track or retrieve an order"]], [28*mm,65*mm,77*mm], True))
story += [P("Staff examples", "Heading")]
story.append(table([
["Method","Endpoint","Purpose"],["PATCH","/api/orders/{id}","Change order status"],["POST","/api/orders/{id}/payments","Confirm an in-person payment"],["GET","/api/kitchen/orders","Kitchen order queue"],["GET/PUT","/api/admin/settings","Read/change restaurant settings"]], [28*mm,65*mm,77*mm], True))
story += [P("Authentication: a successful login returns a random 256-bit bearer token. The frontend sends it in Authorization: Bearer ... on protected requests. The server hashes the token for lookup; the raw token is not stored in the session database table.", "Callout")]

story += section("7. WebSocket: port 8081")
story += [P("WebSocket begins as an HTTP Upgrade request on TCP and then changes protocols. It becomes a persistent, full-duplex connection: the browser and server can both send messages without a full new HTTP request each time. In development, Vite exposes it at /ws and rewrites that path before forwarding to ws://localhost:8081.", "Body"),
P("Channel model", "Heading")]
story.append(table([
["Channel","Purpose","Required authorization"],["staff / kitchen","All order events","Staff session token or kitchen access code"],["order","One order's updates","Tracking token, signed owner or staff"],["user","All orders of a customer","Customer session token"]], [39*mm,66*mm,65*mm], True))
story += [P("Subscription example", "Heading"), code('{"type":"SUBSCRIBE","channel":"staff","token":"<staff-session-token>"}'), P("Event example", "Heading"), code('{"type":"ORDER_STATUS_CHANGED","orderId":42,"orderNumber":"TB-261113-0042","status":"PREPARING","message":"Order TB-261113-0042 is being prepared"}')]
story += [P("Publishing", "Heading"), P("NotificationService publishes ORDER_CREATED, ORDER_STATUS_CHANGED or PAYMENT_UPDATED. LiveWebSocketServer maintains concurrent sets of staff, order-specific and user-specific subscribers. HTTP or TCP workers call the shared WebSocket publisher, and matching clients receive JSON immediately.", "Body"), P("Frontend reconnection", "Heading"), P("socketService.js reconnects automatically after connection loss. It restarts at 1 second, then 2, 4, 8 and at most 15 seconds. When the socket opens again, it sends the subscription message again.")]

story += section("8. Raw TCP Server: port 5000")
story += [P("The TCP server demonstrates direct socket programming. It creates a ServerSocket, binds host and port, then continuously calls accept(). Each accepted Socket represents one client connection. One acceptor thread dispatches that socket to a bounded ThreadPoolExecutor.", "Body")]
story.append(table([
["TCP setting","Default","Meaning"],["TCP_HOST","127.0.0.1","Local-only by default"],["TCP_PORT","5000","Listening port"],["TCP_MAX_CLIENTS","32","Worker threads"],["TCP_MAX_QUEUE","16","Waiting connections"],["TCP_IDLE_TIMEOUT_SECONDS","30","Close after no complete message"],["TCP_MAX_MESSAGE_BYTES","64 KB","Maximum complete line"]], [65*mm,30*mm,75*mm], True))
story += [P("Protocol format", "Heading"), P("The protocol is newline-delimited JSON (NDJSON). Each complete line is one request and produces exactly one response line. Characters are UTF-8 encoded. A newline terminates a request."), code('{"type":"PING"}\n'), code('{"type":"PONG","message":"TableBite server is running"}\n')]
story += [P("Supported requests", "Heading")]
story.append(table([
["Request","Who can use it"],["PING","Anyone"],["GET_MENU","Anyone"],["AUTH","Anyone, then state becomes authenticated"],["NEW_ORDER","Guest QR order or authenticated staff order"],["ORDER_STATUS_UPDATE","Staff with allowed role"],["GET_ORDERS","Staff"],["GET_ORDER / GET_ORDER_STATUS","Staff, owner or tracking-token holder"]], [90*mm,80*mm], True))
story += [P("Connection and authentication", "Heading"), P("A TCP connection begins anonymous. It must send AUTH with a session bearer token or the kitchen access code. The server stores the authenticated User only in the handler for that connection. Role or userId fields in later messages are ignored. The correct identity comes from the verified AUTH state."),
P("Two AUTH examples", "Heading"), code('{"type":"AUTH","token":"<session-token>"}\n'), code('{"type":"AUTH","accessCode":"kitchen123"}\n')]
story += [P("Error handling", "Heading"), P("Every protocol error is a JSON response with type=ERROR and a code. A normal error does not necessarily close the connection. Fatal conditions include a non-terminated message exceeding 1 MB, an idle timeout, shutdown, or five failed AUTH attempts."), code('{"type":"ERROR","success":false,"code":"INVALID_JSON","message":"Invalid JSON request"}\n')]

story += section("9. Frontend Networking")
story += [P("Vite serves the Vue application on port 5173 over HTTP or port 5174 over HTTPS. The HTTPS server exists so phones can access camera APIs over the local Wi-Fi. Browsers commonly restrict camera access to secure contexts, so a public LAN address needs HTTPS in development."),
P("Proxy behavior", "Heading")]
story.append(table([
["Browser URL","Vite proxy target","Destination"],["/api/...","http://localhost:8080","HTTP REST server"],["/uploads/...","http://localhost:8080","Uploaded images"],["/ws","ws://localhost:8081","WebSocket server, path rewritten"]], [55*mm,55*mm,60*mm], True))
story += [P("This means the browser normally sees one origin, such as http://localhost:5173, rather than separately managing cross-origin requests to 8080 and 8081. In production, a reverse proxy can expose /api, /uploads and /ws on HTTPS and forward them to the Java servers.", "Callout")]

story += section("10. Ordering Data Flow")
story += [P("A complete dine-in order demonstrates how ports and application layers interact.", "Heading")]
story += [bullets(["The user scans a QR code containing a secure table token.","The Vue app resolves the token through GET /api/tables/{qrToken}.","The cart is sent to POST /api/orders through the Vite proxy to port 8080.","Router and CustomerRoutes validate the JSON and call OrderService.","OrderService loads the table and menu items and computes totals on the server.","OrderRepository executes the order creation transaction in PostgreSQL.","The server creates a payment record and status-history row.","NotificationService publishes ORDER_CREATED to staff WebSocket subscribers.","The response gives an order number and secret tracking token.","The tracking page subscribes to the order channel on port 8081.","Later status changes are published over WebSocket and remain available through HTTP polling."]), P("Why server-side pricing matters", "Callout")]
story += [P("The server reads current menu prices and computes subtotal, tax and service charge. It does not trust a client-calculated total. Each order item stores item_name and unit_price snapshots so later menu changes do not alter historical order totals.")]

story += section("11. Concurrency and Resource Limits")
story += [P("HTTP concurrency", "Heading"), P("HttpApiServer uses a fixed pool of 16 threads. Several customer and staff requests can execute concurrently. The pool prevents an unbounded number of threads from being created.", "Body"),
P("TCP concurrency", "Heading")]
story += [bullets(["A single acceptor thread waits on ServerSocket.accept().","Accepted sockets are submitted to a ThreadPoolExecutor.","The executor has 32 worker threads by default.","It can hold up to 16 additional connections in an ArrayBlockingQueue.","When workers and queue are full, the next connection receives SERVER_BUSY and is closed.","Each connection has a 30-second complete-message idle timeout.","TcpLineReader checks messages incrementally and rejects lines over 64 KB."]), P("WebSocket concurrency", "Heading"), P("Java-WebSocket runs its own network thread. Subscriber collections use ConcurrentHashMap-backed key sets so HTTP and TCP threads can publish safely while clients connect or disconnect.", "Body")]

story += section("12. Database and Transactions")
story += [P("PostgreSQL is the persistent source of truth. SchemaMigrator applies idempotent migration scripts V1 through V4 automatically at startup when DB_AUTO_MIGRATE is enabled.", "Body")]
story.append(table([
["Area","Implementation"],["Entity relationships","Users, tables, categories, menu items, orders, items, history and payments"],["Order creation","Transaction locks dishes, inserts order/items/payment/history, marks table occupied"],["Status change","Locks order row; validates transition and role; records history"],["Payment","Locks order; prevents duplicate confirmation"],["Duplicate retry","Partial unique index on idempotency_key"]], [55*mm,115*mm], True))
story += [P("Pessimistic locking example", "Heading"), code("BEGIN;\nSELECT ... FROM menu_items WHERE ... FOR SHARE;\nINSERT INTO orders ...;\nINSERT INTO order_items ...;\nINSERT INTO payments ...;\nINSERT INTO order_status_history ...;\nCOMMIT;")]

story += section("13. Security")
story.append(table([
["Threat / concern","TableBite approach"],["Passwords","PBKDF2-HMAC-SHA256, 210,000 iterations, 16-byte salt"],["Session tokens","256-bit random; SHA-256 hash stored in database"],["Authorization","Role checks on every HTTP/TCP operation"],["SQL injection","Prepared statements throughout JDBC repositories"],["Table guessing","128-bit random QR tokens instead of numeric IDs"],["Uploads","JPEG/PNG/WebP magic-byte validation, random names, size limit"],["Rate limiting","Failed login/kitchen-code lockouts"],["TCP LAN exposure","Loopback binding by default; plain text must stay trusted"]], [55*mm,115*mm], True))
story += [P("Order privacy", "Heading"), P("A customer needs ownership or a secret tracking token to read one order. The HTTP 404 behavior is kept private, and WebSocket order subscriptions apply the same owner/staff/token checks.")]

story += section("14. Setup and Running")
story += [P("Required software", "Heading")]
story += [bullets(["JDK 21 or newer","Apache Maven 3.9 or a Maven executable","Node.js 20+ and npm","PostgreSQL 13+","A database named tablebite_db and suitable credentials"]), P("Database setup", "Heading"), code("psql -U postgres -f backend/scripts/setup-database.sql")]
story += [P("Start everything on Windows Git Bash", "Heading"), code("export DB_USER=postgres\nexport DB_PASSWORD=<your-password>\n./run.sh")]
story += [P("The run script starts the Java backend and both Vite servers. It also chooses a JDK 21+, builds the backend and installs frontend dependencies when missing.", "Callout")]
story += [P("Start backend manually", "Heading"), code("cd backend\n$env:JAVA_HOME = \"C:\\Program Files\\Eclipse Adoptium\\jdk-21...\"\n$env:DB_USER = \"postgres\"\n$env:DB_PASSWORD = \"password\"\nmvn -q compile dependency:build-classpath \"-Dmdep.outputFile=target/runtime-classpath.txt\" \"-Dmdep.includeScope=runtime\"\njava -cp \"target/classes;$(Get-Content target/runtime-classpath.txt -Raw)\" com.tablebite.Main")]
story += [P("Useful endpoints", "Heading")]
story.append(table([
["Use","Address"],["Customer site","http://localhost:5173/"],["Kitchen","http://localhost:5173/kitchen"],["Staff login","http://localhost:5173/staff/login"],["Admin","http://localhost:5173/admin"],["HTTP health","http://localhost:8080/api/health"],["WebSocket","ws://localhost:8081"],["TCP","127.0.0.1:5000"]], [55*mm,115*mm], True))

story += section("15. Troubleshooting")
story.append(table([
["Symptom","Likely cause","Check / fix"],["Port already in use","Another process bound 8080, 8081 or 5000","Stop process or change environment port"],["PowerShell Java 8 / 17","Wrong JAVA_HOME or PATH","Select JDK 21+ and rebuild"],["Database connection fails","PostgreSQL not running or credentials wrong","Check DB_USER/DB_PASSWORD and database"],["Phone cannot open app","LAN firewall or wrong address","Allow Node.js private network; use 5174 HTTPS"],["WebSocket offline","Vite proxy or backend 8081 stopped","Check /ws proxy and WebSocket log"],["TCP SERVER_BUSY","Workers and queue saturated","Lower load or increase TCP_MAX_CLIENTS/QUEUE"],["TCP idle timeout","No complete line for 30 seconds","Send complete newline-terminated JSON"],["Camera unavailable","Not HTTPS or not permitted","Use https://LAN-IP:5174 and accept certificate"]], [48*mm,55*mm,67*mm], True))
story += [P("Windows port check example", "Heading"), code("netstat -ano | findstr :8080\nnetstat -ano | findstr :8081\nnetstat -ano | findstr :5000")]

story += section("16. Glossary and Practical Summary")
story += [P("Key terms", "Heading")]
story.append(table([
["Term","Meaning"],["Bind","Associate a server socket with an IP address and port"],["Listen","Tell the OS to accept incoming TCP connections for a socket"],["Accept","Create a Socket object for one connected client"],["Port","A 16-bit transport endpoint number identifying an application service"],["Thread pool","Pre-created worker threads reused for concurrent work"],["NDJSON","One JSON object on each newline"],["Bearer token","Secret presented to prove an authenticated session"],["Tracking token","Secret allowing one guest to track an order"],["Poll fallback","Repeated HTTP checks when WebSocket notifications are unavailable"],["Idempotency","Repeated submissions produce the same order instead of duplicates"]], [48*mm,122*mm], True))
story += [P("Final summary", "Heading"), P("TableBite demonstrates a distributed, multi-protocol application: the Vue frontend communicates over HTTP and WebSocket through Vite, while Java exposes REST APIs, WebSockets and a custom NDJSON TCP protocol. PostgreSQL provides durable data and JDBC transactions protect pricing, orders and payments. Port separation allows each protocol to scale and operate independently, while the shared services and database maintain a consistent system state.")]

# Header and footer
def on_page(canvas, doc):
    canvas.saveState()
    canvas.setStrokeColor(colors.HexColor("#D5DEE5")); canvas.setLineWidth(0.4)
    canvas.line(20*mm, 12*mm, 190*mm, 12*mm)
    canvas.setFont("Helvetica", 7); canvas.setFillColor(colors.HexColor("#607D8B"))
    canvas.drawString(20*mm, 8*mm, "TableBite — Project Overview")
    canvas.drawRightString(190*mm, 8*mm, f"Page {doc.page}")
    canvas.restoreState()

# Cover without header handled by page number, adding footer to all pages is acceptable.
doc=SimpleDocTemplate(OUTPUT, pagesize=A4, rightMargin=20*mm, leftMargin=20*mm, topMargin=18*mm, bottomMargin=16*mm, title="TableBite Complete Project Overview", author="TableBite Project")
doc.build(story, onFirstPage=on_page, onLaterPages=on_page)
print(OUTPUT)
