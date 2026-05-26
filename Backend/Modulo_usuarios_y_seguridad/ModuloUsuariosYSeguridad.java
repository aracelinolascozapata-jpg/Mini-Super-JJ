package puente;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet("/SeguridadServlet")
public class seguridad extends HttpServlet {
    private static final long serialVersionUID = 1L;
    
    String url = "jdbc:oracle:thin:@localhost:1521:xe";
    String user = "soft";
    String pass = "soft";

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try {
            Class.forName("oracle.jdbc.OracleDriver");
            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                String sql = "SELECT l.ID_LOGIN, l.CORREO, r.ROL FROM LOGIN l " +
                             "JOIN EMPLEADOS e ON l.ID_EMPLEADO = e.ID_EMPLEADO " +
                             "JOIN ROL r ON e.ID_ROL = r.ID_ROL";
                
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql);

                StringBuilder json = new StringBuilder("[");
                while (rs.next()) {
                    json.append("{")
                        .append("\"id\":").append(rs.getInt("ID_LOGIN")).append(",")
                        .append("\"usuario\":\"").append(rs.getString("CORREO")).append("\",")
                        .append("\"rol\":\"").append(rs.getString("ROL")).append("\"")
                        .append("},");
                }
                if (json.length() > 1) json.setLength(json.length() - 1);
                json.append("]");
                
                out.print(json.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/plain;charset=UTF-8");
        PrintWriter out = response.getWriter();
        
        String operacion = request.getParameter("operacion");

        try {
            Class.forName("oracle.jdbc.OracleDriver");
            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                
                conn.setAutoCommit(false); // Para asegurar que si falla una tabla, no se guarde a medias

                // ---------------- A) CREAR NUEVO USUARIO ----------------
                if ("crear".equals(operacion)) {
                    String nombre = request.getParameter("nombre");
                    String apellido = request.getParameter("apellido");
                    String correo = request.getParameter("correo");
                    String password = request.getParameter("password");
                    int idRol = Integer.parseInt(request.getParameter("idRol"));

                    // 1. Insertamos en EMPLEADOS (Asumimos que SEQ_EMPLEADOS existe)
                    String sqlEmp = "INSERT INTO EMPLEADOS (id_empleado, id_rol, nombre, ap_paterno, correo) " +
                                    "VALUES (SEQ_EMPLEADOS.NEXTVAL, ?, ?, ?, ?)";
                    try (PreparedStatement psEmp = conn.prepareStatement(sqlEmp)) {
                        psEmp.setInt(1, idRol);
                        psEmp.setString(2, nombre);
                        psEmp.setString(3, apellido);
                        psEmp.setString(4, correo);
                        psEmp.executeUpdate();
                    }

                    // 2. Insertamos en LOGIN amarrando el ID_EMPLEADO recién creado
                    String sqlLogin = "INSERT INTO LOGIN (id_login, id_empleado, correo, contrasena) " +
                                      "VALUES (SEQ_LOGIN.NEXTVAL, SEQ_EMPLEADOS.CURRVAL, ?, ?)";
                    try (PreparedStatement psLogin = conn.prepareStatement(sqlLogin)) {
                        psLogin.setString(1, correo);
                        psLogin.setString(2, password);
                        psLogin.executeUpdate();
                    }
                    
                    conn.commit();
                    out.print("ok");
                } 
                
                // ---------------- B) EDITAR USUARIO EXISTENTE ----------------
                else if ("editar".equals(operacion)) {
                    String idLogin = request.getParameter("idLogin");
                    String idRol = request.getParameter("idRol");
                    String password = request.getParameter("password");

                    // 1. Actualizar Rol
                    String sqlRol = "UPDATE EMPLEADOS SET ID_ROL = ? WHERE ID_EMPLEADO = " +
                                    "(SELECT ID_EMPLEADO FROM LOGIN WHERE ID_LOGIN = ?)";
                    try (PreparedStatement psRol = conn.prepareStatement(sqlRol)) {
                        psRol.setInt(1, Integer.parseInt(idRol));
                        psRol.setInt(2, Integer.parseInt(idLogin));
                        psRol.executeUpdate();
                    }

                    // 2. Actualizar Contraseña (si escribió algo)
                    if (password != null && !password.isEmpty()) {
                        String sqlPass = "UPDATE LOGIN SET CONTRASENA = ? WHERE ID_LOGIN = ?";
                        try (PreparedStatement psPass = conn.prepareStatement(sqlPass)) {
                            psPass.setString(1, password);
                            psPass.setInt(2, Integer.parseInt(idLogin));
                            psPass.executeUpdate();
                        }
                    }
                    
                    conn.commit();
                    out.print("ok");
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
            out.print("error: " + e.getMessage());
        }
    }
}
