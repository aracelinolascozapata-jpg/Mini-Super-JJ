package puente;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/reporte")
public class reporte extends HttpServlet {
    private static final long serialVersionUID = 1L;

    String url = "jdbc:oracle:thin:@localhost:1521:xe";
    String user = "soft";
    String pass = "soft";

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        // Recibimos la clave limpia sin acentos
        String tipo = request.getParameter("tipo"); 
        String fechaDesde = request.getParameter("desde");
        String fechaHasta = request.getParameter("hasta");

        System.out.println("Solicitud de reporte recibida. Tipo: " + tipo + ", Desde: " + fechaDesde + ", Hasta: " + fechaHasta);

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("oracle.jdbc.OracleDriver");
            conn = DriverManager.getConnection(url, user, pass);

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            // 1. VENTAS GENERALES POR PERÍODO
            if ("ventas".equals(tipo)) {
                String sql = "SELECT p.nombre AS PRODUCTO, SUM(d.cantidad) AS CANTIDAD_VENDIDA, SUM(d.subtotal) AS TOTAL_RECAUDADO " +
                             "FROM Detalles_venta d " +
                             "JOIN Productos p ON d.id_producto = p.id_producto " +
                             "JOIN Ventas v ON d.id_venta = v.id_venta " +
                             "WHERE TRUNC(v.fecha) BETWEEN TO_DATE(?, 'YYYY-MM-DD') AND TO_DATE(?, 'YYYY-MM-DD') " +
                             "GROUP BY p.nombre " +
                             "ORDER BY SUM(d.subtotal) DESC";
                
                ps = conn.prepareStatement(sql);
                ps.setString(1, fechaDesde);
                ps.setString(2, fechaHasta);
                rs = ps.executeQuery();

                while (rs.next()) {
                    if (!first) json.append(",");
                    json.append("{")
                        .append("\"producto\":\"").append(rs.getString("PRODUCTO")).append("\",")
                        .append("\"cantidad\":").append(rs.getInt("CANTIDAD_VENDIDA")).append(",")
                        .append("\"total\":").append(rs.getDouble("TOTAL_RECAUDADO"))
                        .append("}");
                    first = false;
                }
            }
            
            // 2. TOP PRODUCTOS MÁS VENDIDOS
            else if ("top".equals(tipo)) {
                String sql = "SELECT p.nombre AS PRODUCTO, SUM(d.cantidad) AS CANTIDAD_VENDIDA, SUM(d.subtotal) AS TOTAL_RECAUDADO " +
                             "FROM Detalles_venta d " +
                             "JOIN Productos p ON d.id_producto = p.id_producto " +
                             "JOIN Ventas v ON d.id_venta = v.id_venta " +
                             "WHERE TRUNC(v.fecha) BETWEEN TO_DATE(?, 'YYYY-MM-DD') AND TO_DATE(?, 'YYYY-MM-DD') " +
                             "GROUP BY p.nombre " +
                             "ORDER BY SUM(d.cantidad) DESC";
                
                ps = conn.prepareStatement(sql);
                ps.setString(1, fechaDesde);
                ps.setString(2, fechaHasta);
                rs = ps.executeQuery();

                while (rs.next()) {
                    if (!first) json.append(",");
                    json.append("{")
                        .append("\"producto\":\"").append(rs.getString("PRODUCTO")).append("\",")
                        .append("\"cantidad\":").append(rs.getInt("CANTIDAD_VENDIDA")).append(",")
                        .append("\"total\":").append(rs.getDouble("TOTAL_RECAUDADO"))
                        .append("}");
                    first = false;
                }
            }

            // 3. ROTACIÓN DE INVENTARIO
            else if ("rotacion".equals(tipo)) {
                String sql = "SELECT p.nombre AS PRODUCTO, SUM(d.cantidad) AS CANTIDAD_VENDIDA, p.cantidad AS STOCK_ACTUAL " +
                             "FROM Detalles_venta d " +
                             "JOIN Productos p ON d.id_producto = p.id_producto " +
                             "JOIN Ventas v ON d.id_venta = v.id_venta " +
                             "WHERE TRUNC(v.fecha) BETWEEN TO_DATE(?, 'YYYY-MM-DD') AND TO_DATE(?, 'YYYY-MM-DD') " +
                             "GROUP BY p.nombre, p.cantidad " +
                             "ORDER BY SUM(d.cantidad) DESC";
                
                ps = conn.prepareStatement(sql);
                ps.setString(1, fechaDesde);
                ps.setString(2, fechaHasta);
                rs = ps.executeQuery();

                while (rs.next()) {
                    if (!first) json.append(",");
                    json.append("{")
                        .append("\"producto\":\"").append(rs.getString("PRODUCTO")).append("\",")
                        .append("\"cantidad\":").append(rs.getInt("CANTIDAD_VENDIDA")).append(",")
                        .append("\"total\":").append(rs.getDouble("STOCK_ACTUAL")) 
                        .append("}");
                    first = false;
                }
            }
            
            // 4. COMPARATIVA DE INGRESOS POR PAGO
            else if ("pagos".equals(tipo)) {
                String sql = "SELECT pa.pago AS PRODUCTO, COUNT(v.id_venta) AS CANTIDAD_VENDIDA, SUM(v.total) AS TOTAL_RECAUDADO " +
                             "FROM Ventas v " +
                             "JOIN Pago pa ON v.id_pago = pa.id_pago " +
                             "WHERE TRUNC(v.fecha) BETWEEN TO_DATE(?, 'YYYY-MM-DD') AND TO_DATE(?, 'YYYY-MM-DD') " +
                             "GROUP BY pa.pago";
                
                ps = conn.prepareStatement(sql);
                ps.setString(1, fechaDesde);
                ps.setString(2, fechaHasta);
                rs = ps.executeQuery();

                while (rs.next()) {
                    if (!first) json.append(",");
                    json.append("{")
                        .append("\"producto\":\"").append(rs.getString("PRODUCTO")).append("\",")
                        .append("\"cantidad\":").append(rs.getInt("CANTIDAD_VENDIDA")).append(",")
                        .append("\"total\":").append(rs.getDouble("TOTAL_RECAUDADO"))
                        .append("}");
                    first = false;
                }
            }

            json.append("]");
            String resultadoFinal = json.toString();
            System.out.println("JSON enviado a la web: " + resultadoFinal);
            out.print(resultadoFinal);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error en Servlet reporte: " + e.getMessage());
            out.print("[]");
        } finally {
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (SQLException e) { }
        }
    }
}
