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

@WebServlet("/caja")
public class caja extends HttpServlet {
    private static final long serialVersionUID = 1L;

    String url = "jdbc:oracle:thin:@localhost:1521:xe";
    String user = "soft";
    String pass = "soft";

    // 1. OBTENER ESTADO ACTUAL DE LA CAJA Y SUMATORIAS REALES (doGet)
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        String accion = request.getParameter("accion");

        try {
            Class.forName("oracle.jdbc.OracleDriver");
            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                Statement st = conn.createStatement();

                // A) Verificar si hay una caja abierta hoy (id_estado = 1)
                if ("verificarEstado".equals(accion)) {
                    String sql = "SELECT id_caja, fondo_inicial FROM Caja WHERE id_estado = 1 AND TRUNC(fecha) = TRUNC(SYSDATE)";
                    ResultSet rs = st.executeQuery(sql);
                    
                    if (rs.next()) {
                        out.print("{\"abierta\":true, \"id_caja\":" + rs.getLong("id_caja") + ", \"fondo_inicial\":" + rs.getDouble("fondo_inicial") + "}");
                    } else {
                        out.print("{\"abierta\":false}");
                    }
                }
                
                // B) Calcular los ingresos y egresos reales del día para el resumen
                else if ("calcularResumen".equals(accion)) {
                    double fondoInicial = Double.parseDouble(request.getParameter("fondo"));
                    
                    // 1. Sumar ventas en Efectivo de hoy (id_pago = 1)
                    double ventasEfectivo = 0;
                    ResultSet rsVentasEfe = st.executeQuery("SELECT SUM(total) AS suma FROM Ventas WHERE id_pago = 1 AND TRUNC(fecha) = TRUNC(SYSDATE)");
                    if (rsVentasEfe.next()) ventasEfectivo = rsVentasEfe.getDouble("suma");

                    // 2. Sumar ventas con Mercado Pago de hoy (id_pago = 2)
                    double ventasMercado = 0;
                    ResultSet rsVentasMer = st.executeQuery("SELECT SUM(total) AS suma FROM Ventas WHERE id_pago = 2 AND TRUNC(fecha) = TRUNC(SYSDATE)");
                    if (rsVentasMer.next()) ventasMercado = rsVentasMer.getDouble("suma");

                    // 3. Sumar pagos/abonos a Adeudos registrados hoy (Se calcula comparando el monto original vs pendiente si tuvieras tabla de historial, aquí asumimos un estimado o consulta base)
                    // Nota: Como la tabla Adeudos no guarda el abono individual por fecha en el script, ponemos un valor referencial o lo dejamos listo
                    double pagosAdeudos = 0; 
                    
                    // 4. Sumar Egresos (Pagos a proveedores en la tabla Pedidos de hoy)
                    double egresos = 0;
                    ResultSet rsEgresos = st.executeQuery("SELECT SUM(pago) AS suma FROM Pedidos WHERE TRUNC(fecha) = TRUNC(SYSDATE)");
                    if (rsEgresos.next()) egresos = rsEgresos.getDouble("suma");

                    double totalTeorico = fondoInicial + ventasEfectivo + pagosAdeudos - egresos;

                    StringBuilder json = new StringBuilder("{");
                    json.append("\"ventasEfectivo\":").append(ventasEfectivo).append(",")
                        .append("\"ventasMercado\":").append(ventasMercado).append(",")
                        .append("\"pagosAdeudos\":").append(pagosAdeudos).append(",")
                        .append("\"egresos\":").append(egresos).append(",")
                        .append("\"totalTeorico\":").append(totalTeorico)
                        .append("}");
                    out.print(json.toString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            out.print("{}");
        }
    }

    // 2. PROCESAR APERTURA O CIERRE EN LA BASE DE DATOS (doPost)
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/plain;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String operacion = request.getParameter("operacion");
        int idEmpleado = 1; // Empleado por defecto obligatorio en tu BD

        Connection conn = null;

        try {
            Class.forName("oracle.jdbc.OracleDriver");
            conn = DriverManager.getConnection(url, user, pass);
            conn.setAutoCommit(false);

            // A) ABRIR CAJA
            if ("abrir".equals(operacion)) {
                double fondo = Double.parseDouble(request.getParameter("fondo"));
                
                // Insertamos con id_estado = 1 (Abierta)
                String sql = "INSERT INTO Caja (id_caja, id_estado, id_empleado, fecha, fondo_inicial) " +
                             "VALUES (SEQ_CAJA.NEXTVAL, 1, ?, SYSDATE, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, idEmpleado);
                    ps.setDouble(2, fondo);
                    ps.executeUpdate();
                }
                conn.commit();
                out.print("ok");
            }
            
            // B) CERRAR CAJA
            else if ("cerrar".equals(operacion)) {
                long idCaja = Long.parseLong(request.getParameter("idCaja"));
                double conteo = Double.parseDouble(request.getParameter("conteo"));
                double diferencia = Double.parseDouble(request.getParameter("diferencia"));
                // El campo "nota" no existe en tu tabla Caja original, por lo que actualizamos estrictamente los campos numéricos
                
                // Actualizamos a id_estado = 2 (Cerrada)
                String sql = "UPDATE Caja SET id_estado = 2, conteo_fisico = ?, diferencia = ? WHERE id_caja = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setDouble(1, conteo);
                    ps.setDouble(2, diferencia);
                    ps.setLong(3, idCaja);
                    ps.executeUpdate();
                }
                conn.commit();
                out.print("ok");
            }

        } catch (Exception e) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) { }
            out.print("error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { if (conn != null) { conn.setAutoCommit(true); conn.close(); } } catch (SQLException e) { }
        }
    }
}
