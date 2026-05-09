package puente;

import java.io.IOException; 
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/LoginServlet")
public class servidor extends HttpServlet {
    private static final long serialVersionUID = 1L;

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    	String correo = request.getParameter("correo");
        String password = request.getParameter("password");
  
        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();

        String url = "jdbc:oracle:thin:@localhost:1521:xe";
        String user = "soft"; 
        String passDB = "soft"; 

        try {
            // ESTA LÍNEA SOLUCIONA EL ERROR DEL DRIVER
            Class.forName("oracle.jdbc.OracleDriver");
            
            try (Connection conn = DriverManager.getConnection(url, user, passDB)) {
            	String sql = "SELECT e.ID_ROL FROM LOGIN l " +
                        "JOIN EMPLEADOS e ON l.ID_EMPLEADO = e.ID_EMPLEADO " +
                        "WHERE l.CORREO = ? AND l.CONTRASENA = ?";
                             
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, correo);
                    ps.setString(2, password);
                    
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                        	int idRol = rs.getInt("ID_ROL");
                        	out.print(idRol);
                        } else {
                            out.print("error");
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            out.print("error_servidor");
        }
    }
}
