import NewPurchaseRow from './NewPurchaseRow';
import BigButton from '../../big-button/BigButton';
import { publish } from '../../../events';

function PurchasesTable({ purchases, addNewPurchase }) {
  console.log(
    'Rendering PurchasesTable component with property purchases:',
    purchases
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
          <NewPurchaseRow
            addNewPurchase={addNewPurchase}
          />
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
      <BigButton
        className='new-purchase-save primary'
        onClickHandler={() => publish('createNewPurchase')}
      >
        Запази
      </BigButton>
    </div>
  );
}

export default PurchasesTable;
