import NewPurchaseRow from './NewPurchaseRow';

function PurchasesTable({ purchases, showNewPurchase}) {
  console.log(
    'Rendering PurchasesTable component with property purchases:',
    purchases
  );
  console.log(
    'Rendering PurchasesTable component with property showNewPurchase:', showNewPurchase
  );
  return (
    <div className='purchases-table'>
      <table>
        <thead>
          <tr>
            <th>Цена</th>
            <th>Дата</th>
            <th>Магазин</th>
            <th>Намаление</th>
          </tr>
        </thead>
        <tbody>
          {showNewPurchase && <NewPurchaseRow />}
          {purchases.map((purchase) => {
            return (
              <tr>
                <td>{purchase.price}</td>
                <td>{purchase.date}</td>
                <td>
                  <img src={`/${purchase.store.iconID}-icon.svg`} alt='' />
                  {purchase.store.name}
                </td>
                <td>
                  {purchase.discount && (
                    <img src='discount-icon.svg' alt='&#10004;' />
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

export default PurchasesTable;
