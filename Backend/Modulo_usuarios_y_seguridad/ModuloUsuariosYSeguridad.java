package puente;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet("/SeguridadServlet")
public class seguridad extends HttpServlet {
    
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        String url = "jdbc:oracle:thin:@localhost:1521:xe";
        String user = "soft";
        String pass = "soft";

        try {
            Class.forName("oracle.jdbc.OracleDriver");
            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                // Consulta para traer los datos reales
                String sql = "SELECT l.ID_LOGIN, l.CORREO, r.ROL FROM LOGIN l " +
                             "JOIN EMPLEADOS e ON l.ID_EMPLEADO = e.ID_EMPLEADO " +
                             "JOIN ROL r ON e.ID_ROL = r.ID_ROL";
                
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql);

                // Construimos un JSON manual sencillo
                StringBuilder json = new StringBuilder("[");
                while (rs.next()) {
                    json.append("{")
                        .append("\"id\":").append(rs.getInt("ID_LOGIN")).append(",")
                        .append("\"usuario\":\"").append(rs.getString("CORREO")).append("\",")
                        .append("\"rol\":\"").append(rs.getString("ROL")).append("\"")
                        .append("},");
                }
                if (json.length() > 1) json.setLength(json.length() - 1); // Quitar última coma
                json.append("]");
                
                out.print(json.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
        }
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String idLogin = request.getParameter("idLogin");
        String idRol = request.getParameter("idRol");
        String password = request.getParameter("password");
        String revocar = request.getParameter("revocar");

        PrintWriter out = response.getWriter();
        String url = "jdbc:oracle:thin:@localhost:1521:xe";
        String user = "soft";
        String pass = "soft";

        try {
            Class.forName("oracle.jdbc.OracleDriver");
            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                
                // 1. Actualizar el Rol en la tabla EMPLEADOS
                String sqlRol = "UPDATE EMPLEADOS SET ID_ROL = ? WHERE ID_EMPLEADO = " +
                                "(SELECT ID_EMPLEADO FROM LOGIN WHERE ID_LOGIN = ?)";
                
                try (PreparedStatement psRol = conn.prepareStatement(sqlRol)) {
                    psRol.setInt(1, Integer.parseInt(idRol));
                    psRol.setInt(2, Integer.parseInt(idLogin));
                    psRol.executeUpdate();
                }

                // 2. Actualizar la contraseña si el usuario escribió algo
                if (password != null && !password.isEmpty()) {
                    String sqlPass = "UPDATE LOGIN SET CONTRASENA = ? WHERE ID_LOGIN = ?";
                    try (PreparedStatement psPass = conn.prepareStatement(sqlPass)) {
                        psPass.setString(1, password);
                        psPass.setInt(2, Integer.parseInt(idLogin));
                        psPass.executeUpdate();
                    }
                }

                out.print("ok");
            }
        } catch (Exception e) {
            e.printStackTrace();
            out.print("error");
        }
    }
}
