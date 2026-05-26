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

@WebServlet("/adeudos")
public class adeudos extends HttpServlet {
    private static final long serialVersionUID = 1L;

    String url = "jdbc:oracle:thin:@localhost:1521:xe";
    String user = "soft";
    String pass = "soft";

    // 1. CARGAR CLIENTES Y ADEUDOS PENDIENTES (doGet)
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        String accion = request.getParameter("accion");

        try {
            Class.forName("oracle.jdbc.OracleDriver");
            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                Statement st = conn.createStatement();
                
                // A) Si el HTML pide la lista de clientes
                if ("cargarClientes".equals(accion)) {
                    ResultSet rs = st.executeQuery("SELECT id_cliente, nombre || ' ' || ap_paterno AS nombre_completo FROM Clientes");
                    StringBuilder json = new StringBuilder("[");
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) json.append(",");
                        json.append("{")
                            .append("\"id\":").append(rs.getLong("id_cliente")).append(",")
                            .append("\"nombre\":\"").append(rs.getString("nombre_completo")).append("\"")
                            .append("}");
                        first = false;
                    }
                    json.append("]");
                    out.print(json.toString());
                }
                
                // B) Si el HTML pide la lista de adeudos pendientes
                else if ("cargarAdeudos".equals(accion)) {
                    // Consultamos uniendo Adeudos, Clientes y Estado
                    String sql = "SELECT a.id_adeudos, c.nombre || ' ' || c.ap_paterno AS cliente, " +
                                 "a.fecha_limite, a.monto_pendiente, e.estado " +
                                 "FROM Adeudos a " +
                                 "JOIN Clientes c ON a.id_cliente = c.id_cliente " +
                                 "JOIN Estado e ON a.id_estado = e.id_estado " +
                                 "WHERE a.monto_pendiente > 0";
                    ResultSet rs = st.executeQuery(sql);
                    
                    StringBuilder json = new StringBuilder("[");
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) json.append(",");
                        json.append("{")
                            .append("\"id_adeudo\":").append(rs.getLong("id_adeudos")).append(",")
                            .append("\"cliente\":\"").append(rs.getString("cliente")).append("\",")
                            .append("\"fecha_limite\":\"").append(rs.getDate("fecha_limite")).append("\",")
                            .append("\"monto_pendiente\":").append(rs.getDouble("monto_pendiente")).append(",")
                            .append("\"estado\":\"").append(rs.getString("estado")).append("\"")
                            .append("}");
                        first = false;
                    }
                    json.append("]");
                    out.print(json.toString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            out.print("[]");
        }
    }

    // 2. GUARDAR NUEVO ADEUDO O REGISTRAR UN PAGO (doPost)
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/plain;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String tipoOperacion = request.getParameter("operacion");
        Connection conn = null;

        try {
            Class.forName("oracle.jdbc.OracleDriver");
            conn = DriverManager.getConnection(url, user, pass);
            conn.setAutoCommit(false);

            // A) REGISTRAR NUEVO ADEUDO
            if ("nuevoAdeudo".equals(tipoOperacion)) {
                long idCliente = Long.parseLong(request.getParameter("idCliente"));
                double monto = Double.parseDouble(request.getParameter("monto"));
                
                // OJO: En tu script, el adeudo va amarrado a una venta obligatoriamente.
                // Aquí creamos una "Venta a crédito" (id_pago = 1, id_empleado = 1) para justificar el adeudo.
                String sqlVenta = "INSERT INTO Ventas (id_venta, id_pago, id_empleado, fecha, total) " +
                                  "VALUES (SEQ_VENTAS.NEXTVAL, 1, 1, SYSDATE, ?)";
                try (PreparedStatement psVenta = conn.prepareStatement(sqlVenta)) {
                    psVenta.setDouble(1, monto);
                    psVenta.executeUpdate();
                }

                // Insertamos el Adeudo amarrado a esa venta (id_estado = 1 representa "Pendiente")
                // Le damos 30 días del vencimiento (SYSDATE + 30)
                String sqlAdeudo = "INSERT INTO Adeudos (id_adeudos, id_estado, id_cliente, id_venta, fecha_limite, monto_pendiente) " +
                                   "VALUES (SEQ_ADEUDOS.NEXTVAL, 1, ?, SEQ_VENTAS.CURRVAL, SYSDATE + 30, ?)";
                try (PreparedStatement psAdeudo = conn.prepareStatement(sqlAdeudo)) {
                    psAdeudo.setLong(1, idCliente);
                    psAdeudo.setDouble(2, monto);
                    psAdeudo.executeUpdate();
                }
                conn.commit();
                out.print("ok");
            } 
            
            // B) REGISTRAR UN PAGO (ABONO)
            else if ("pagarAdeudo".equals(tipoOperacion)) {
                long idAdeudo = Long.parseLong(request.getParameter("idAdeudo"));
                double abono = Double.parseDouble(request.getParameter("abono"));

                // Descontamos el monto pendiente
                String sqlAbono = "UPDATE Adeudos SET monto_pendiente = monto_pendiente - ? WHERE id_adeudos = ?";
                try (PreparedStatement psAbono = conn.prepareStatement(sqlAbono)) {
                    psAbono.setDouble(1, abono);
                    psAbono.setLong(2, idAdeudo);
                    psAbono.executeUpdate();
                }

                // Si llegó a cero o menos, le cambiamos el estado a 2 (ej. "Pagado")
                String sqlVerificar = "UPDATE Adeudos SET id_estado = 2 WHERE id_adeudos = ? AND monto_pendiente <= 0";
                try (PreparedStatement psVer = conn.prepareStatement(sqlVerificar)) {
                    psVer.setLong(1, idAdeudo);
                    psVer.executeUpdate();
                }
                
                conn.commit();
                out.print("ok");
            }
            
            else if ("nuevoCliente".equals(tipoOperacion)) {
                String nombre = request.getParameter("nombre");
                String telefonoStr = request.getParameter("telefono");
                
                // Usamos una secuencia para generarle su ID numérico automáticamente
                String sqlCli = "INSERT INTO Clientes (id_cliente, nombre, telefono) VALUES (SEQ_CLIENTES.NEXTVAL, ?, ?)";
                
                try (PreparedStatement psCli = conn.prepareStatement(sqlCli)) {
                    psCli.setString(1, nombre);
                    
                    // Validamos si el usuario escribió un teléfono o lo dejó en blanco
                    if (telefonoStr != null && !telefonoStr.trim().isEmpty()) {
                        psCli.setLong(2, Long.parseLong(telefonoStr.trim()));
                    } else {
                        psCli.setNull(2, java.sql.Types.NUMERIC);
                    }
                    
                    psCli.executeUpdate();
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
