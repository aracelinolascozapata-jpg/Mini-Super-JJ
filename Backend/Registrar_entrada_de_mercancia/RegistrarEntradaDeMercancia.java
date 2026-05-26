package puente;

import java.io.*;
import java.sql.*;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet("/MercanciaServlet")
public class entrada_mercancia extends HttpServlet {
    
    String url = "jdbc:oracle:thin:@localhost:1521:xe";
    String user = "soft"; // Asegúrate de usar SYSTEM o soft según tu configuración
    String pass = "soft";

    // 1. CARGAR PROVEEDORES Y PRODUCTOS (Para autocompletar)
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        
        try {
            Class.forName("oracle.jdbc.OracleDriver");
            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                StringBuilder json = new StringBuilder("{ \"proveedores\": [");
                Statement st = conn.createStatement();
                
                // Cargar Proveedores (Tabla: Proveedor)
                ResultSet rsProv = st.executeQuery("SELECT nombre FROM Proveedor");
                boolean firstProv = true;
                while (rsProv.next()) {
                    if (!firstProv) json.append(",");
                    json.append("\"").append(rsProv.getString("nombre")).append("\"");
                    firstProv = false;
                }
                
                json.append("], \"productos\": [");
                
                // Cargar Productos (Tabla: Productos)
                ResultSet rsProd = st.executeQuery("SELECT nombre FROM Productos");
                boolean firstProd = true;
                while (rsProd.next()) {
                    if (!firstProd) json.append(",");
                    json.append("\"").append(rsProd.getString("nombre")).append("\"");
                    firstProd = false;
                }
                
                json.append("] }");
                out.print(json.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            out.print("{ \"error\": \"" + e.getMessage() + "\" }");
        }
    }

    // 2. GUARDAR ENTRADA (LÓGICA UPSERT)
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String nombreProd = request.getParameter("producto");
        int cantEntrada = Integer.parseInt(request.getParameter("cantidad"));
        double precioComp = Double.parseDouble(request.getParameter("pCompra"));
        double precioVenta = Double.parseDouble(request.getParameter("pVenta"));
        String nombreProv = request.getParameter("proveedor");
        double totalCompra = Double.parseDouble(request.getParameter("total"));
        double montoPagado = Double.parseDouble(request.getParameter("pago"));
        PrintWriter out = response.getWriter();

        try {
            Class.forName("oracle.jdbc.OracleDriver");
            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                conn.setAutoCommit(false);

                // 1. Crear el Pedido (Cabecera)
                String sqlPedido = "INSERT INTO PEDIDOS (ID_PEDIDO, ID_PROVEEDOR, PAGO, FECHA) " +
                                   "VALUES (SEQ_PEDIDOS.NEXTVAL, (SELECT id_proveedor FROM Proveedor WHERE nombre = ?), ?, SYSDATE)";
                try (PreparedStatement ps1 = conn.prepareStatement(sqlPedido)) {
                    ps1.setString(1, nombreProv);
                    ps1.setDouble(2, montoPagado);
                    ps1.executeUpdate();
                }

                // 2. Intentamos ACTUALIZAR el producto (Si ya existe)
                String sqlStock = "UPDATE Productos SET cantidad = cantidad + ?, precio_compra = ?, precio_venta = ? WHERE nombre = ?";
                int filasStock;
                try (PreparedStatement psUpdate = conn.prepareStatement(sqlStock)) {
                    psUpdate.setInt(1, cantEntrada);
                    psUpdate.setDouble(2, precioComp);
                    psUpdate.setDouble(3, precioVenta);
                    psUpdate.setString(4, nombreProd);
                    filasStock = psUpdate.executeUpdate();
                }

                // 3. Si filasStock es 0, el producto NO existe. Lo INSERTAMOS.
                if (filasStock == 0) {
                    String sqlNuevo = "INSERT INTO Productos (id_producto, id_proveedor, nombre, precio_compra, precio_venta, cantidad, stock_minimo) " +
                                      "VALUES (SEQ_PRODUCTOS.NEXTVAL, (SELECT id_proveedor FROM Proveedor WHERE nombre = ?), ?, ?, ?, ?, 10)";
                    try (PreparedStatement psInsert = conn.prepareStatement(sqlNuevo)) {
                        psInsert.setString(1, nombreProv);
                        psInsert.setString(2, nombreProd);
                        psInsert.setDouble(3, precioComp);
                        psInsert.setDouble(4, precioVenta);
                        psInsert.setInt(5, cantEntrada);
                        psInsert.executeUpdate();
                    }
                }

                // 4. Registrar en PRODUCTOS_X_PEDIDOS
                String sqlDetalle = "INSERT INTO Productos_x_Pedidos (id_producto, id_pedido, cantidad, precio, subtotal) " +
                                    "VALUES ((SELECT id_producto FROM Productos WHERE nombre = ?), SEQ_PEDIDOS.CURRVAL, ?, ?, ?)";
                try (PreparedStatement ps3 = conn.prepareStatement(sqlDetalle)) {
                    ps3.setString(1, nombreProd);
                    ps3.setInt(2, cantEntrada);
                    ps3.setDouble(3, precioComp);
                    ps3.setDouble(4, (cantEntrada * precioComp));
                    ps3.executeUpdate();
                }

                conn.commit(); 
                out.print("ok");
            }
        } catch (Exception e) {
            e.printStackTrace();
            out.print("error: " + e.getMessage());
        }
    }
}
