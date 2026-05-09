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

@WebServlet("/ventas")
public class ventas extends HttpServlet {
    private static final long serialVersionUID = 1L;

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();

        // 1. Recibir los datos generales de la venta
        String totalStr = request.getParameter("total");
        String metodoPago = request.getParameter("metodoPago");
        
        // 2. Recibir los arreglos con los detalles del carrito
        String[] productos = request.getParameterValues("productos");
        String[] cantidades = request.getParameterValues("cantidades");
        String[] precios = request.getParameterValues("precios");
        String[] subtotales = request.getParameterValues("subtotales");

        if (totalStr == null || productos == null) {
            out.print("Error: Datos del carrito incompletos");
            return;
        }

        double total = Double.parseDouble(totalStr);
        Connection conn = null;
        PreparedStatement psVenta = null;
        PreparedStatement psDetalle = null;
        PreparedStatement psStock = null;
        ResultSet rsClave = null;

        try {
            // Configura tu conexión a Oracle 11g
            Class.forName("oracle.jdbc.OracleDriver");
            conn = DriverManager.getConnection("jdbc:oracle:thin:@localhost:1521:XE", "soft", "soft");
            
            // Desactivar autocommit para proteger la transacción (Venta + Detalles + Stock)
            conn.setAutoCommit(false); 

            // Insertar Venta principal con secuencia de Oracle
            String sqlVenta = "INSERT INTO tabla_ventas (id_venta, total, metodo_pago, fecha) VALUES (SEQ_VENTAS.NEXTVAL, ?, ?, SYSDATE)";
            String[] returnId = { "ID_VENTA" };
            psVenta = conn.prepareStatement(sqlVenta, returnId);
            psVenta.setDouble(1, total);
            psVenta.setString(2, metodoPago);
            psVenta.executeUpdate();

            int idVenta = 0;
            rsClave = psVenta.getGeneratedKeys();
            if (rsClave.next()) {
                idVenta = rsClave.getInt(1); // Obtener el ID de la venta recién insertada
            }

            // Preparar consultas para insertar el detalle y descontar el inventario
            String sqlDetalle = "INSERT INTO tabla_detalle_venta (id_detalle, id_venta, producto, cantidad, precio, subtotal) VALUES (SEQ_DETALLE_VENTA.NEXTVAL, ?, ?, ?, ?, ?)";
            String sqlStock = "UPDATE tabla_inventario SET stock = stock - ? WHERE nombre_producto = ?";
            
            psDetalle = conn.prepareStatement(sqlDetalle);
            psStock = conn.prepareStatement(sqlStock);

            // Recorrer los arreglos recibidos desde HTML e insertarlos
            for (int i = 0; i < productos.length; i++) {
                String prod = productos[i];
                int cant = Integer.parseInt(cantidades[i]);
                double prec = Double.parseDouble(precios[i]);
                double sub = Double.parseDouble(subtotales[i]);

                // Agregar al lote del Detalle
                psDetalle.setInt(1, idVenta);
                psDetalle.setString(2, prod);
                psDetalle.setInt(3, cant);
                psDetalle.setDouble(4, prec);
                psDetalle.setDouble(5, sub);
                psDetalle.addBatch();

                // Agregar al lote del Stock
                psStock.setInt(1, cant);
                psStock.setString(2, prod);
                psStock.addBatch();
            }

            // Ejecutar las sentencias por lote
            psDetalle.executeBatch();
            psStock.executeBatch();

            conn.commit(); // Confirmar la transacción
            out.print("Venta registrada en la base de datos con éxito.");

        } catch (Exception e) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) { }
            out.print("Error al registrar venta: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (rsClave != null) rsClave.close();
                if (psVenta != null) psVenta.close();
                if (psDetalle != null) psDetalle.close();
                if (psStock != null) psStock.close();
                if (conn != null) { conn.setAutoCommit(true); conn.close(); }
            } catch (SQLException e) { }
        }
    }
}
