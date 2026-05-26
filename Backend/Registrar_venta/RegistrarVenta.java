package puente;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/ventas")
public class ventas extends HttpServlet {
    private static final long serialVersionUID = 1L;

    String url = "jdbc:oracle:thin:@localhost:1521:xe";
    String user = "soft";
    String pass = "soft";

    // 1. CARGAR PRODUCTOS (Para autocompletar nombre, precio y stock en el HTML)
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try {
            Class.forName("oracle.jdbc.OracleDriver");
            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                // Traemos el ID, Nombre, Precio de Venta y Cantidad (Stock disponible)
                String sql = "SELECT id_producto, nombre, precio_venta, cantidad FROM Productos WHERE cantidad > 0";
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql);

                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    int idProd = rs.getInt("ID_PRODUCTO");
                    String nombreProd = rs.getString("NOMBRE");
                    double precioVenta = rs.getDouble("PRECIO_VENTA");
                    int cantidadStock = rs.getInt("CANTIDAD");

                    if (nombreProd != null) {
                        nombreProd = nombreProd.replace("\"", "\\\"");
                    }

                    json.append("{")
                        .append("\"id\":\"").append(idProd).append("\",")
                        .append("\"nombre\":\"").append(nombreProd).append("\",")
                        .append("\"precio\":").append(precioVenta).append(",")
                        .append("\"stock\":").append(cantidadStock)
                        .append("}");
                    first = false;
                }
                json.append("]");
                out.print(json.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            out.print("[]");
            System.out.println("Error al cargar productos en doGet: " + e.getMessage());
        }
    }

    // 2. REGISTRAR LA VENTA Y DESCONTAR STOCK EN LOTE
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/plain;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String totalStr = request.getParameter("total");
        String metodoPago = request.getParameter("metodoPago");
        String[] productos = request.getParameterValues("productos"); // Nombres o IDs
        String[] cantidades = request.getParameterValues("cantidades");
        String[] precios = request.getParameterValues("precios");
        String[] subtotales = request.getParameterValues("subtotales");

        if (totalStr == null || productos == null) {
            out.print("error: Datos incompletos");
            return;
        }

        double total = Double.parseDouble(totalStr);
        int idPago = 1; // Por defecto Efectivo
        if ("Mercado Pago".equalsIgnoreCase(metodoPago)) {
            idPago = 2;
        }
        int idEmpleado = 1;
        Connection conn = null;
        PreparedStatement psVenta = null;
        PreparedStatement psDetalle = null;
        PreparedStatement psStock = null;

        try {
            Class.forName("oracle.jdbc.OracleDriver");
            conn = DriverManager.getConnection(url, user, pass);
            
            // --- NUEVO CANDADO DE SEGURIDAD: VERIFICAR CAJA ABIERTA ---
            String sqlCaja = "SELECT id_caja FROM Caja WHERE id_estado = 1 AND TRUNC(fecha) = TRUNC(SYSDATE)";
            try (Statement stCaja = conn.createStatement();
                 ResultSet rsCaja = stCaja.executeQuery(sqlCaja)) {
                if (!rsCaja.next()) {
                    out.print("error: Operación denegada. No se puede cobrar porque la caja está cerrada.");
                    return; // Detenemos la ejecución aquí
                }
            }
            conn.setAutoCommit(false); 

            // 1. Insertar Venta (Asegúrate de tener creada la secuencia SEQ_VENTAS y la tabla VENTAS)
            String sqlVenta = "INSERT INTO Ventas (id_venta, id_pago, id_empleado, fecha, total) " +
                    "VALUES (SEQ_VENTAS.NEXTVAL, ?, ?, SYSDATE, ?)";
            psVenta = conn.prepareStatement(sqlVenta);
            psVenta.setInt(1, idPago);
            psVenta.setInt(2, idEmpleado);
            psVenta.setDouble(3, total);
            psVenta.executeUpdate();

            // 2. Preparar consultas para Detalle y Stock
            // Usamos SEQ_VENTAS.CURRVAL para enlazar el detalle con la venta recién creada
            String sqlDetalle = "INSERT INTO Detalles_venta (id_producto, id_venta, precio_unitario, subtotal, cantidad) " +
                    "VALUES ((SELECT id_producto FROM Productos WHERE nombre = ?), SEQ_VENTAS.CURRVAL, ?, ?, ?)";
            
            // Descontamos de la tabla real 'Productos'
            String sqlStock = "UPDATE Productos SET cantidad = cantidad - ? WHERE nombre = ?";
            
            psDetalle = conn.prepareStatement(sqlDetalle);
            psStock = conn.prepareStatement(sqlStock);

            for (int i = 0; i < productos.length; i++) {
                String prod = productos[i];
                double prec = Double.parseDouble(precios[i]);
                double sub = Double.parseDouble(subtotales[i]);
                int cant = Integer.parseInt(cantidades[i]);

                // Batch para Detalles_venta
                psDetalle.setString(1, prod);
                psDetalle.setDouble(2, prec);
                psDetalle.setDouble(3, sub);
                psDetalle.setInt(4, cant);
                psDetalle.addBatch();

                // Batch para Stock
                psStock.setInt(1, cant);
                psStock.setString(2, prod);
                psStock.addBatch();
            }

            psDetalle.executeBatch();
            psStock.executeBatch();
            
            StringBuilder alertas = new StringBuilder();
            String sqlCheck = "SELECT nombre FROM Productos WHERE nombre = ? AND cantidad <= stock_minimo";
            try (PreparedStatement psCheck = conn.prepareStatement(sqlCheck)) {
                for (String prod : productos) {
                    psCheck.setString(1, prod);
                    try (ResultSet rs = psCheck.executeQuery()) {
                        if (rs.next()) {
                            if (alertas.length() > 0) alertas.append(",");
                            alertas.append(rs.getString("NOMBRE"));
                        }
                    }
                }
            }

            conn.commit(); 
            if (alertas.length() > 0) {
                out.print("ok|" + alertas.toString());
            } else {
                out.print("ok");
            }

        }
        
        
        catch (Exception e) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) { }
            out.print("error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (psVenta != null) psVenta.close();
                if (psDetalle != null) psDetalle.close();
                if (psStock != null) psStock.close();
                if (conn != null) { conn.setAutoCommit(true); conn.close(); }
            } catch (SQLException e) { }
        }
    }
}
